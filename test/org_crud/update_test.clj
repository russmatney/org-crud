(ns org-crud.update-test
  (:require
   [org-crud.update :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [babashka.fs :as fs]
   [clojure.set :as set]
   [org-crud.util :as util]
   [org-crud.core :as org]
   [clojure.string :as string]))


(defn test-fixtures
  [f]
  (fs/copy
    (str (fs/cwd) "/test/org_crud/update-test-before.org")
    (str (fs/cwd) "/test/org_crud/update-test.org")
    {:replace-existing true})
  (fs/copy
    (str (fs/cwd) "/test/org_crud/update-test-root-item-before.org")
    (str (fs/cwd) "/test/org_crud/update-test-root-item.org")
    {:replace-existing true})
  (f))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (fs/cwd) "/test/org_crud/update-test.org"))

(def root-item-filepath
  (str (fs/cwd) "/test/org_crud/update-test-root-item.org"))

(defn ->items []
  (org/path->flattened-items org-filepath))

(defn ->item []
  (org/path->nested-item org-filepath))

(defn get-headline [pred-map]
  (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update anything
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-counts-test
  (testing "updates an item, compares before/after counts"
    (let [ct (-> (->items) count)]
      (sut/update! org-filepath
                   (get-headline {:org/name "finished with brackets"})
                   {:org/tags "newtag"})
      (is (= ct (-> (->items) count))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update status (done/not-started/current)
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

(comment
  (->>
    (->items)
    (map :org/name)))

(deftest update-status-more-statuses-test
  (testing "adds a tag then checks that the correct statuses are set"
    (sut/update! org-filepath
                 (get-headline {:org/name "finished with brackets"})
                 {:org/tags "newtag"})

    (is (= :status/not-started
           (-> {:org/name "todo with words"}
               get-headline :org/status)))
    (is (= :status/done
           (-> {:org/name "finished with brackets"}
               get-headline :org/status)))
    (is (= :status/done
           (-> {:org/name "finished with words"}
               get-headline :org/status)))
    (is (= :status/skipped
           (-> {:org/name "skipped with words"}
               get-headline :org/status)))
    (is (= :status/in-progress
           (-> {:org/name "started with a dash"}
               get-headline :org/status)))
    (is (= :status/in-progress
           (-> {:org/name "started with a word"}
               get-headline :org/status)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update tags on headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-tags-test
  (testing "updates a headline's tags"
    (let [pred-map    {:org/name "add/remove tags"}
          tags        #{"test" "yother"}
          another-tag "anothertag"
          empty       #{}
          k           :org/tags
          headline    (get-headline pred-map)]
      (is (= (get headline k) empty))

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
      (is (= (:org/tags (get-headline pred-map)) #{"newtag"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update priorities on headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-priority-test
  (testing "updates a headline's priority"
    (let [pred-map {:org/name "add/remove priority"}
          priority "A"
          k        :org/priority
          headline (get-headline pred-map)
          name     (:org/name headline)]
      (is (= nil (get headline k)))

      ;; set priority
      (sut/update! org-filepath (get-headline pred-map) {k priority})
      (let [headline (get-headline pred-map)]
        (is (= priority (get headline k)))
        ;; make sure name doesn't change
        (is (= name (:org/name headline))))

      ;; set different priority
      (sut/update! org-filepath (get-headline pred-map) {k "C"})
      (let [headline (get-headline pred-map)]
        (is (= "C" (get headline k)))
        ;; make sure name doesn't change
        (is (= name (:org/name headline))))

      ;; set same priority
      (sut/update! org-filepath (get-headline pred-map) {k "C"})
      (let [headline (get-headline pred-map)]
        (is (= "C" (get headline k)))
        ;; make sure name doesn't change
        (is (= name (:org/name headline))))

      ;; remove priority
      (sut/update! org-filepath (get-headline pred-map) {k nil})
      (let [headline (get-headline pred-map)]
        (is (= nil (get headline k)))))))

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

      (testing "additional key-values move to a unique list"
        (update! {:org.prop/hello "world"})
        (is (= (->props) {:org.prop/hello "world"}))
        (update! {:org.prop/hello "duty"})
        (is (= (->props) {:org.prop/hello ["duty" "world"]}))
        (update! {:org.prop/hello "world"})
        (is (= (->props) {:org.prop/hello ["duty" "world"]})))

      (testing "vals can be removed with :remove"
        (update! {:org.prop/some-val "world"})
        (is (= (-> (->props) :org.prop/some-val) "world"))
        (update! {:org.prop/some-val :remove})
        (is (= (-> (->props) :org.prop/some-val) nil)))

      (testing "lists can be cleared with :remove"
        (update! {:org.prop/some-vals ["hey" "world"]})
        (is (= (-> (->props) :org.prop/some-vals) ["hey" "world"]))
        (update! {:org.prop/some-vals :remove})
        (is (= (-> (->props) :org.prop/some-vals) nil)))

      (testing "vals can be removed from lists with [:remove val]"
        (update! {:org.prop/some-list-val "world"})
        (is (= (-> (->props) :org.prop/some-list-val) "world"))
        (update! {:org.prop/some-list-val "hello"})
        (is (= (-> (->props) :org.prop/some-list-val) ["hello" "world"]))
        (update! {:org.prop/some-list-val [:remove "world"]})
        (is (= (-> (->props) :org.prop/some-list-val) "hello"))))))

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
    (is (= (->multi-test-repo-ids) "my/new-repo"))

    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (= (->multi-test-repo-ids) ["my/new-repo" "my/other-repo"]))))

(deftest update-properties-multiple-values-test-remove
  (testing "removes an existing repo-id"
    (multi-test-update ["my/other-repo" "my/new-repo"])
    (is (same-xs? (->multi-test-repo-ids) ["my/other-repo" "my/new-repo"]))
    (multi-test-update [:remove "my/other-repo"])
    (is (= (->multi-test-repo-ids) "my/new-repo"))))

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
;; don't add property-buckets if there are none
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-path-with-fn!-empty-props
  (testing "does not add property buckets to every item"
    (sut/update-path-with-fn! org-filepath (fn [_it] nil))
    (testing "reads an empty map from no/empty property bucket"
      (is
        (->> (slurp org-filepath)
             string/split-lines
             (drop-while (fn [line] (not (= line "* no props on me!"))))
             rest
             ((fn [lines]
                (not (= ":PROPERTIES:" (first lines))))))))))

(comment
  (->>
    (slurp org-filepath)
    (string/split-lines)
    (drop-while (fn [line]
                  (not (= line "* no props on me!"))))
    next)
  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update using id prop
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def same-name-1-id #uuid "c3a82f7c-ecff-4e4c-8047-bc4e11861bb0")
(defn same-name-1 [] (get-headline {:org/id same-name-1-id}))

(def same-name-2-id  #uuid "bcdf8060-e158-4f8c-9c4a-a9f5d58bd890")
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

(deftest write-updated-roundtrip
  (testing "the parsed org items should be equivalent after being written and reparsed"
    (let [parsed-items (->items)]
      (sut/write-updated org-filepath parsed-items
                         {:org.update/reset-last-modified true})
      (is (= (count parsed-items) (count (->items))))
      ;; easier to debug one item than all (below)
      (is (= (->> parsed-items
                  (mapcat :org/items)
                  (take 1)
                  ;; removing the org-section to ease debugging
                  ;; one difference is property capitalization
                  (map #(dissoc % :org-section)))
             (->> (->items)
                  (mapcat :org/items)
                  (take 1)
                  (map #(dissoc % :org-section)))))
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

(defn update-item [update]
  (sut/update! root-item-filepath (parse-item) update))

(deftest update-root-item
  (testing "root-level tags are updated as expected"
    (update-item {:org/tags "newtag"})
    (is (contains? (-> (parse-item) :org/tags) "newtag"))
    (is (contains? (-> (parse-item) :org/tags) "existing")))

  (testing "root-level props are updated as expected"
    (is (= nil (-> (parse-item) :org.prop/new-prop)))
    (sut/update! root-item-filepath  (parse-item) #:org.prop{:new-prop "newprop"})
    (is (= "newprop" (-> (parse-item) :org.prop/new-prop)))))

(deftest update-root-filetags
  (let [{:org/keys [tags]} (parse-item)]
    (is (= #{"post" "sometag" "existing"} tags))
    (update-item {:org/tags (cons "newtag" tags)})
    (is (= #{"post" "sometag" "newtag" "existing"}
           (-> (parse-item) :org/tags)))))

(deftest update-root-with-drawer-id
  (let [{:org/keys [id]} (parse-item)]
    (update-item {:org.prop/my-prop "val"})
    (is (= id (-> (parse-item) :org/id)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; blocks
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

;; code blocks
(deftest update-item-with-code-blocks
  (let [->hl (fn [] (get-headline {:org/name "this node has code"}))
        parse-and-assert-code-blocks
        (fn []
          (let [src-blocks    (->> (->hl) :org/body
                                   (filter (comp #{"SRC" "src"}
                                                 :block-type)))
                [blk-1 blk-2] src-blocks
                [blk-content-1 blk-content-2]
                (->> src-blocks
                     (map (fn [block] (->> block :content (map :text)
                                           (string/join "\n")))))]
            (is (= (:qualifier blk-1) "clojure"))
            (is blk-content-1)
            (is (string/includes? blk-content-1 "my-clj-var"))
            (is (= (:qualifier blk-2) "gdscript"))
            (is blk-content-2)
            (is (string/includes? blk-content-2 "print"))))]
    (testing "reading code blocks"
      (parse-and-assert-code-blocks))

    (testing "adding an org-prop, then re-reading"
      (sut/update! org-filepath (->hl) {:org.prop/some-prop "abc"})
      (is (:org.prop/some-prop (->hl)) "abc")
      (parse-and-assert-code-blocks))

    (testing "setting a todo status, then re-reading"
      (sut/update! org-filepath (->hl) {:org/status :status/in-progress})
      (is (:org/status (->hl)) :status/in-progress)
      (parse-and-assert-code-blocks))))

;; quote blocks
(deftest update-item-with-quote-blocks
  (let [->hl (fn [] (get-headline {:org/name "this node has quotes"}))
        parse-and-assert
        (fn []
          (let [blocks        (->> (->hl) :org/body
                                   (filter (comp #{"QUOTE" "quote"}
                                                 :block-type)))
                [blk-1 blk-2] blocks
                [blk-content-1 blk-content-2]
                (->> blocks
                     (map (fn [block] (->> block :content (map :text)
                                           (string/join "\n")))))]
            (is (= (:qualifier blk-1) "someone"))
            (is blk-content-1)
            (is (string/includes? blk-content-1 "from someone"))
            (is (= (:qualifier blk-2) "another_someone"))
            (is blk-content-2)
            (is (string/includes? blk-content-2 "from another someone"))))]
    (testing "reading quote blocks"
      (parse-and-assert))

    (testing "adding an org-prop, then re-reading"
      (sut/update! org-filepath (->hl) {:org.prop/some-prop "abc"})
      (is (:org.prop/some-prop (->hl)) "abc")
      (parse-and-assert))

    (testing "setting a todo status, then re-reading"
      (sut/update! org-filepath (->hl) {:org/status :status/in-progress})
      (is (:org/status (->hl)) :status/in-progress)
      (parse-and-assert))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; images
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-item-image
  (let [->hl #(get-headline {:org/name-string "blog supporting screenshots and clips"})
        parse-and-assert
        (fn []
          (is (string/includes? (-> (->hl) :org/images first :image/path) "gifs/Peek"))
          (is (= (-> (->hl) :org/images first :image/extension) "mp4")))]

    (testing "parses image data" (parse-and-assert))
    (testing "updating the node doesn't break the image parse"
      (let [hl           (->hl)
            updated-tags (conj (:org/tags hl) "newtag")]
        (sut/update! org-filepath hl {:org/tags updated-tags})
        (parse-and-assert)
        (is (= (-> (->hl) :org/tags) updated-tags))))

    ;; TODO update image properties (name/caption)
    ;; TODO update image path
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; update without touching :file/last-modified
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest update-ignore-last-modified
  (let [item  (->item)
        og-lm (:file/last-modified item)]
    (is item)
    (is og-lm)
    (testing "update normally bumps last-modified"
      (sut/update! org-filepath item {:org/tags "newtag"})
      (is (not (= og-lm (-> (->item) :file/last-modified)))))

    (testing "update optionally maintains last-modified"
      (let [lm (-> (->item) :file/last-modified)]
        (sut/update! org-filepath item {:org/tags                       "newtag"
                                        :org.update/reset-last-modified true})
        ;; the 'reset' last-modified can shift it's precision
        ;; e.g. "2023-04-20T19:10:58.611746733Z" vs "2023-04-20T19:10:58.611746Z"
        ;; so we hack it to drop the 'Z' and then compare
        (let [read-lm (-> (->item) :file/last-modified)
              read-lm (->> read-lm butlast (apply str))]
          (is read-lm)
          (is (string/includes? lm read-lm)))))))
