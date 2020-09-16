(ns org-crud.update-test
  (:require
   [org-crud.update :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [me.raynes.fs :as fs]
   [clojure.set :as set]
   [org-crud.headline :as headline]
   [org-crud.util :as util]
   [org-crud.core :as org]))

(defn test-fixtures
  [f]
  (fs/copy
    (str fs/*cwd* "/test/org_crud/update-test-before.org")
    (str fs/*cwd* "/test/org_crud/update-test.org"))
  (fs/copy
    (str fs/*cwd* "/test/org_crud/update-test-root-item-before.org")
    (str fs/*cwd* "/test/org_crud/update-test-root-item.org"))
  (binding [headline/*multi-prop-keys* #{:repo-ids}]
    (f)))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/update-test.org"))

(def root-item-filepath
  (str (str fs/*cwd*) "/test/org_crud/update-test-root-item.org"))

(defn ->items []
  (org/path->flattened-items org-filepath))

(defn get-headline [pred-map]
  (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update status (done/not-started/current)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; for now, just the [ ] or [X].
(deftest update-status-test
  (testing "updates a todo's status"
    (let [pred-map {:org/name "mark me status"}
          orig     :status/not-started
          new      :status/done
          k        :org/status]
      (sut/update! org-filepath (get-headline pred-map) {k new})
      (let [headline (get-headline pred-map)]
        (is (= new (get headline k))))

      (sut/update! org-filepath (get-headline pred-map) {k orig})
      (let [headline (get-headline pred-map)]
        (is (= orig (get headline k))))

      (sut/update! org-filepath (get-headline pred-map) {k new})
      (let [headline (get-headline pred-map)]
        (is (= new (get headline k))))

      (sut/update! org-filepath (get-headline pred-map) {k orig})
      (let [headline (get-headline pred-map)]
        (is (= orig (get headline k)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update tags on headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-tags-test
  (testing "updates a headline's tags"
    (let [pred-map    {:org/name "add/remove tags"}
          tags        #{"test" "yother"}
          another-tag "anothertag"
          empty       #{}
          k           :org/tags]
      (let [headline (get-headline pred-map)]
        (is (= (get headline k) empty)))

      ;; add two tags, expect both
      (sut/update! org-filepath (get-headline pred-map) {k tags})
      (let [headline (get-headline pred-map)]
        (is (= (get headline k) tags)))

      ;; add one more, expect all three
      (sut/update! org-filepath (get-headline pred-map) {k another-tag})
      (let [headline (get-headline pred-map)]
        (is (set/subset? #{another-tag} (get headline k)))
        (is (set/subset? tags (get headline k))))

      ;; set empty, has no affect
      (sut/update! org-filepath (get-headline pred-map) {k empty})
      (let [headline (get-headline pred-map)]
        (is (set/subset? #{another-tag} (get headline k)))
        (is (set/subset? tags (get headline k)))))))

(deftest remove-tags-test
  (let [pred-map {:org/name "add/remove tags"}]
    (testing "initially clear"
      (is (= (:org/tags (get-headline pred-map)) #{})))

    (testing "adds two tags"
      (sut/update! org-filepath (get-headline pred-map) {:org/tags "newtag"})
      (sut/update! org-filepath (get-headline pred-map) {:org/tags "othertag"})
      (is (= (:org/tags (get-headline pred-map)) #{"newtag" "othertag"})))

    (testing "removes one"
      (sut/update! org-filepath (get-headline pred-map) {:org/tags "newtag"})
      (sut/update! org-filepath (get-headline pred-map) {:org/tags "othertag"})
      (sut/update! org-filepath (get-headline pred-map)
                   {:org/tags [:remove "othertag"]})
      (is (= (:org/tags (get-headline pred-map)) #{"newtag"})))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update property on property buckets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; (get-headline {:org/name "basic properties"})
(deftest update-properties-test
  (testing "updates a headline's properties"
    (let [pred-map {:org/name "basic properties"}
          ->props  (fn []
                     (->> pred-map
                          get-headline
                          (util/ns-select-keys "org.prop")
                          (#(dissoc % :org.prop/id))))
          update!  (fn [up]
                     (sut/update!
                       org-filepath (get-headline pred-map) up))]
      (testing "reads an empty map from no/empty property bucket"
        (is (= (->props) {})))

      (testing "sets a basic key-value"
        (update! {:org.prop/hello "world"})
        (is (= (->props) {:org.prop/hello "world"})))

      (testing "overwrites a basic key-value"
        (update! {:org.prop/hello "world"})
        (is (= (->props) {:org.prop/hello "world"}))
        (update! {:org.prop/hello "duty"})
        (is (= (->props) {:org.prop/hello "duty"}))
        (update! {:org.prop/hello "world"})
        (is (= (->props) {:org.prop/hello "world"})))

      (testing "clears a basic key-value"
        (update! {:org.prop/hello "world"})
        (update! {:org.prop/hello nil})
        (is (= (->props) {}))))))

(defn same-xs? [x y]
  (= (set x) (set y)))

(def multi-prop-pred-map {:org/name "add/remove multi-properties"})

(defn ->multi-test-repo-ids []
  (-> (get-headline multi-prop-pred-map)
      :org.prop/repo-ids))

(defn multi-test-update [repo-ids]
  (sut/update!
    org-filepath (get-headline multi-prop-pred-map)
    {:org.prop/repo-ids repo-ids}))

(deftest update-properties-multiple-values-test-add
  (testing "adds new repo-ids"
    (multi-test-update "my/new-repo")
    (is (same-xs? (->multi-test-repo-ids) ["my/new-repo"]))

    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))))

(deftest update-properties-multiple-values-test-remove
  (testing "removes an existing repo-id"
    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))
    (multi-test-update [:remove "my/other-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/new-repo"]))))

(deftest update-properties-multiple-values-test-add-existing
  (testing "does not add an existing repo-id"
    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))
    (multi-test-update "my/new-repo")
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))))

(deftest update-properties-multiple-values-test-remove-non-existing
  (testing "remove can be called when one does not exist"
    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))
    (multi-test-update [:remove "my/never-added-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; consistent property key order
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn ->prop-order-headline []
  (get-headline {:org/name "consistent prop order"}))

(defn prop-order-update [up]
  (sut/update! org-filepath (->prop-order-headline) up))

(defn prop-order-props []
  (-> (->prop-order-headline)
      (#(util/ns-select-keys "org.prop" %))
      (dissoc :org.prop/id)))

;; the test result is not useful yet - need to eye-ball the order in the org file
(deftest update-property-order
  (testing "updating property buckets results in a determined order (to prevent annoying commit cruft)"
    (prop-order-update #:org.prop{:a "1" :b "2"})
    (is (= (prop-order-props) #:org.prop{:a "1" :b "2"}))

    ;; note that running this can swap the order of a/b in the org file
    (prop-order-update #:org.prop{:c "hi"})
    (is (= (prop-order-props) #:org.prop{:a "1" :b "2" :c "hi"}))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update using id prop
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def same-name-1-id "c3a82f7c-ecff-4e4c-8047-bc4e11861bb0")
(defn same-name-1 [] (get-headline {:org/id same-name-1-id}))

(def same-name-2-id  "bcdf8060-e158-4f8c-9c4a-a9f5d58bd890")
(defn same-name-2 [] (get-headline {:org/id same-name-2-id}))

(deftest ensure-same-name-with-diff-ids
  (testing "ensures the two items used to test this have the same name"
    (is (= (:org/name (same-name-1)) (:org/name (same-name-2))))))

(deftest update-same-name-props
  (testing "updates each item's props and ensures they got the right value"
    (let [v1 (rand-int 100)
          v2 (rand-int 100)]
      (sut/update! org-filepath (same-name-1) {:org.prop/rand v1})
      (sut/update! org-filepath (same-name-2) {:org.prop/rand v2})
      (is (= (-> (same-name-1) :org.prop/rand) (str v1)))
      (is (= (-> (same-name-2) :org.prop/rand) (str v2))))))

(deftest update-same-name-tags
  (testing "updates each item's tags"
    (let [v1 (str (rand-int 100))
          v2 (str (rand-int 100))]
      (sut/update! org-filepath (same-name-1) {:org/tags #{v1}})
      (sut/update! org-filepath (same-name-2) {:org/tags #{v2}})
      (is (contains? (-> (same-name-1) :org/tags) v1))
      (is (not (contains? (-> (same-name-1) :org/tags) v2)))
      (is (contains? (-> (same-name-2) :org/tags) v2))
      (is (not (contains? (-> (same-name-2) :org/tags) v1))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; writing parsed org back to disk
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest write-updated
  (testing "writes the parsed org back to disk properly"
    (let [parsed-items (->items)]
      (sut/write-updated org-filepath parsed-items)
      (is (= (count parsed-items) (count (->items))))
      (is (= (->> parsed-items
                  ;; removing the org-section to ease debugging
                  ;; one difference is property capitalization
                  (map #(dissoc % :org-section)))
             (->> (->items)
                  (map #(dissoc % :org-section))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Updating root items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-item []
  (org/path->nested-item root-item-filepath))

(deftest update-root-item
  (testing "root-level tags are updated as expected"
    (sut/update! root-item-filepath (parse-item) {:org/tags "newtag"})
    (is (contains? (-> (parse-item) :org/tags) "newtag"))
    (is (contains? (-> (parse-item) :org/tags) "existing")))

  (testing "root-level props are updated as expected"
    (is (= nil (-> (parse-item) :org.prop/new-prop)))
    (sut/update! root-item-filepath  (parse-item) #:org.prop{:new-prop "newprop"})
    (is (= "newprop" (-> (parse-item) :org.prop/new-prop)))))
