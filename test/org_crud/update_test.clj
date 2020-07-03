(ns org-crud.update-test
  (:require
   [org-crud.update :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [me.raynes.fs :as fs]
   [clojure.set :as set]
   [org-crud.util :as util]
   [org-crud.core :as org]))


(defn test-fixtures
  [f]
  (fs/copy
    (str fs/*cwd* "/test/org_crud/update-test-before.org")
    (str fs/*cwd* "/test/org_crud/update-test.org"))
  (f))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/update-test.org"))

(defn ->items []
  (org/path->items org-filepath))

(defn get-headline [pred-map]
  (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update status (done/not-started/current)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; for now, just the [ ] or [X].
(deftest update-status-test
  (testing "updates a todo's status"
    (let [pred-map {:name "mark me status"}
          orig     :status/not-started
          new      :status/done
          k        :status]
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
    (let [pred-map    {:name "add/remove tags"}
          tags        #{"test" "yother"}
          another-tag "anothertag"
          empty       #{}
          k           :tags]
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
  (let [pred-map {:name "add/remove tags"}]
    (testing "initially clear"
      (is (= (:tags (get-headline pred-map)) #{})))

    (testing "adds two tags"
      (sut/update! org-filepath (get-headline pred-map) {:tags "newtag"})
      (sut/update! org-filepath (get-headline pred-map) {:tags "othertag"})
      (is (= (:tags (get-headline pred-map)) #{"newtag" "othertag"})))

    (testing "removes one"
      (sut/update! org-filepath (get-headline pred-map) {:tags "newtag"})
      (sut/update! org-filepath (get-headline pred-map) {:tags "othertag"})
      (sut/update! org-filepath (get-headline pred-map)
                   {:tags [:remove "othertag"]})
      (is (= (:tags (get-headline pred-map)) #{"newtag"})))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update property on headline property buckets
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-properties-test
  (testing "updates a headline's properties"
    (let [pred-map {:name "basic properties"}
          ->props  (fn []
                     (-> pred-map
                         get-headline
                         (get :props)
                         (dissoc :id)))
          update!  (fn [up]
                     (sut/update!
                       org-filepath (get-headline pred-map) up))]
      (testing "reads an empty map from no/empty property bucket"
        (is (= (->props) {})))

      (testing "sets a basic key-value"
        (update! {:props {:hello "world"}})
        (is (= (->props) {:hello "world"})))

      (testing "overwrites a basic key-value"
        (update! {:props {:hello "world"}})
        (is (= (->props) {:hello "world"}))
        (update! {:props {:hello "duty"}})
        (is (= (->props) {:hello "duty"}))
        (update! {:props {:hello "world"}})
        (is (= (->props) {:hello "world"})))

      (testing "clears a basic key-value"
        (update! {:props {:hello "world"}})
        (update! {:props {:hello nil}})
        (is (= (->props) {}))))))

(defn same-xs? [x y]
  (= (set x) (set y)))

(def multi-prop-pred-map {:name "add/remove multi-properties"})

(defn ->multi-test-repo-ids []
  (-> (get-headline multi-prop-pred-map)
      :props
      :repo-ids))

(defn multi-test-update [repo-ids]
  (sut/update!
    org-filepath (get-headline multi-prop-pred-map)
    {:props {:repo-ids repo-ids}}))

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
  (get-headline {:name "consistent prop order"}))

(defn prop-order-update [up]
  (sut/update! org-filepath (->prop-order-headline) up))

(defn prop-order-props []
  (-> (->prop-order-headline) :props (dissoc :id)))

;; the test result is not useful yet - need to eye-ball the order in the org file
(deftest update-property-order
  (testing "updating property buckets results in a determined order (to prevent annoying commit cruft)"
    (prop-order-update {:props {:a "1" :b "2"}})
    (is (= (prop-order-props) {:a "1" :b "2"}))

    ;; note that running this can swap the order of a/b in the org file
    (prop-order-update {:props {:c "hi"}})
    (is (= (prop-order-props) {:a "1" :b "2" :c "hi"}))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def new-headline-name ""
  "new headline")

(defn ->new-headline []
  (get-headline {:name new-headline-name}))

(defn add-new-headline [item]
  (sut/add-to-file! org-filepath item :top-level))

(deftest create-new-headline
  (testing "a new, top-level headline is created"
    (is (= nil (->new-headline)))
    (add-new-headline {:name new-headline-name})
    (is (= new-headline-name (:name (->new-headline))))))

(deftest create-new-headline-tags
  (testing "sets tags"
    (is (= nil (->new-headline)))
    (add-new-headline {:name new-headline-name
                       :tags "hi"})
    (is (= #{"hi"} (:tags (->new-headline))))))

(deftest create-new-headline-props
  (testing "sets props"
    (is (= nil (->new-headline)))
    (add-new-headline {:name  new-headline-name
                       :tags  "hi"
                       :props {:hi "bye"}})
    (is (= "bye" (:hi (:props (->new-headline)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new nested headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-new-nested-headline [item context]
  (sut/add-to-file! org-filepath item context))

(deftest create-new-headline-nested
  (testing "a new, top-level headline is created"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (get-headline {:name "parent headline"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))

(deftest create-new-headline-nested-newlines
  (testing "a new, top-level headline is placed in the right spot"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (get-headline {:name
                                            "parent with mid-line asterisk"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; delete a headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def deleted-headline-name ""
  "delete headline")

(def deleted-nested-headline-name ""
  "delete nested headline")

(defn ->deleted-headline []
  (get-headline {:name deleted-headline-name}))

(defn ->deleted-nested-headline []
  (get-headline {:name deleted-nested-headline-name}))

(defn do-delete-headline [item]
  (sut/delete-from-file! org-filepath item))

(deftest delete-headline
  (testing "deletes a headline"
    (is (= deleted-headline-name (:name (->deleted-headline))))
    (do-delete-headline (->deleted-headline))
    (is (= nil (->deleted-headline)))
    (is (= nil (get-headline {:name "deleted more content"})))))


(deftest delete-nested-headline
  (testing "deletes a nested headline"
    (is (= deleted-nested-headline-name (:name (->deleted-nested-headline))))
    (do-delete-headline (->deleted-nested-headline))
    (is (= nil (->deleted-nested-headline)))
    (is (not= nil (get-headline {:name "deleted more content"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; refile an item
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def to-refile "some inbox item")

(defn do-refile [item context]
  (sut/refile-within-file! org-filepath item context))

(deftest refile-headline
  (testing "refiles a headline to the top level"
    (is (= to-refile (:name (get-headline {:name to-refile}))))
    (do-refile (get-headline {:name to-refile}) :top-level)
    (is (= 1 (:level (get-headline {:name to-refile}))))))

(deftest refile-headline-nested
  (testing "refiles a headline under the passed context"
    (is (= to-refile (:name (get-headline {:name to-refile}))))
    (do-refile (get-headline {:name to-refile})
               (get-headline {:name "target todo"}))
    (is (= 4 (:level (get-headline {:name to-refile}))))))

(deftest refile-headline-include-body
  (testing "refiles a headline, including it's body content"
    (is (= to-refile (:name (get-headline {:name to-refile}))))
    (let [body-count (count (:body (get-headline {:name to-refile})))]
      (do-refile (get-headline {:name to-refile})
                 (get-headline {:name "target todo"}))
      (is (not (nil? body-count)))
      (is (= body-count (count (:body (get-headline {:name to-refile}))))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update using id prop
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def same-name-1-id "c3a82f7c-ecff-4e4c-8047-bc4e11861bb0")
(defn same-name-1 [] (get-headline {:id same-name-1-id}))

(def same-name-2-id  "bcdf8060-e158-4f8c-9c4a-a9f5d58bd890")
(defn same-name-2 [] (get-headline {:id same-name-2-id}))

(deftest ensure-same-name-with-diff-ids
  (testing "ensures the two items used to test this have the same name"
    (is (= (:name (same-name-1)) (:name (same-name-2))))))

(deftest update-same-name-props
  (testing "updates each item's props and ensures they got the right value"
    (let [v1 (rand-int 100)
          v2 (rand-int 100)]
      (sut/update! org-filepath (same-name-1) {:props {:rand v1}})
      (sut/update! org-filepath (same-name-2) {:props {:rand v2}})
      (is (= (-> (same-name-1) :props :rand) (str v1)))
      (is (= (-> (same-name-2) :props :rand) (str v2))))))

(deftest update-same-name-tags
  (testing "updates each item's tags"
    (let [v1 (str (rand-int 100))
          v2 (str (rand-int 100))]
      (sut/update! org-filepath (same-name-1) {:tags #{v1}})
      (sut/update! org-filepath (same-name-2) {:tags #{v2}})
      (is (contains? (-> (same-name-1) :tags) v1))
      (is (not (contains? (-> (same-name-1) :tags) v2)))
      (is (contains? (-> (same-name-2) :tags) v2))
      (is (not (contains? (-> (same-name-2) :tags) v1))))))

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
