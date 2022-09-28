(ns org-crud.parse-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [org-crud.parse :as sut]
   [org-crud.schema :as schema]
   org-crud.test-util))

(defn prop-bucket [test-data]
  (->>
    [":PROPERTIES:"
     (when-let [id (:id test-data)]
       (str ":ID:       " id))
     ":END:"]
    (remove nil?)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; basic
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parse-lines-test-basic
  (testing "basic parse-lines works"
    (let [t    "some title"
          node (sut/parse-lines
                 [(str "#+title: " t)])]
      (is (valid schema/item-schema node))
      (is (= (:org/word-count node) 2))
      (is (= (:org/level node) :level/root))
      (is (= (:org/name node) t))))

  (testing "parses file properties, title, created-at, and filetags"
    (let [title      "my special title"
          created-at "20220920:132237"
          id         #uuid "c7dc484f-72d5-4fdb-aca3-05440000f63f"
          node
          (sut/parse-lines
            (concat
              (prop-bucket {:id id})
              [(str "#+TITLE: " title)
               (str "#+filetags: :tools:clojure:")
               (str "#+CREATED_AT: " created-at)]))]
      (is (valid schema/item-schema node))
      (is (= (-> node :org/name) title))
      (is (= (-> node :org/id) id))
      (is (= (-> node :org.prop/created-at) created-at))
      (is (= (-> node :org/tags) #{"tools" "clojure"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; todos
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parses-todos
  (testing "parses todo statuses and ids"
    (let [id-1 #uuid "a7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-2 #uuid "b7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-3 #uuid "c7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-4 #uuid "d7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-5 #uuid "e7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-6 #uuid "f7dc484f-72d5-4fdb-aca3-05440000f63f"
          id-7 #uuid "a8dc484f-72d5-4fdb-aca3-05440000f63f"
          node
          (sut/parse-lines
            (concat
              ["#+TITLE: sample file"
               "* [ ] not started"]
              (prop-bucket {:id id-1})
              ["* [X] completed"]
              (prop-bucket {:id id-2})
              ["* [-] in progress"]
              (prop-bucket {:id id-3})
              ["* TODO not started two"]
              (prop-bucket {:id id-4})
              ["* DONE completed two"]
              (prop-bucket {:id id-5})
              ["* CANCELLED cancelled"]
              (prop-bucket {:id id-6})
              ["* SKIP skipped"]
              (prop-bucket {:id id-7})))

          todos (->> node :org/items (filter :org/status)
                     (map (fn [td] [(:org/id td) td]))
                     (into {}))]
      (is (valid schema/item-schema node))

      (is (= (-> (todos id-1) :org/status) :status/not-started))
      (is (= (-> (todos id-1) :org/name) "not started"))
      (is (= (-> (todos id-4) :org/status) :status/not-started))
      (is (= (-> (todos id-4) :org/name) "not started two"))

      (is (= (-> (todos id-2) :org/status) :status/done))
      (is (= (-> (todos id-2) :org/name) "completed"))
      (is (= (-> (todos id-5) :org/status) :status/done))
      (is (= (-> (todos id-5) :org/name) "completed two"))

      (is (= (-> (todos id-3) :org/status) :status/in-progress))
      (is (= (-> (todos id-3) :org/name) "in progress"))

      (is (= (-> (todos id-6) :org/status) :status/cancelled))
      (is (= (-> (todos id-6) :org/name) "cancelled"))

      (is (= (-> (todos id-7) :org/status) :status/skipped))
      (is (= (-> (todos id-7) :org/name) "skipped"))))

  (testing "ids in headlines and scheduled dates"
    (let [id      #uuid "7daae9e6-ba06-4121-b422-77d7d157fa2b"
          link-id #uuid "c7dc484f-72d5-4fdb-aca3-05440000f63f"
          date    "2022-09-29 Thu"
          node    (sut/parse-lines
                    ["#+title: some title"
                     (str "* [ ] implement that vim clojure lib's intro to clojure in [[id:" link-id "][clerk]]")
                     (str "SCHEDULED: <" date ">")
                     ":PROPERTIES:"
                     (str ":ID:       " id)
                     ":END:"])
          todo    (->> node :org/items first)]
      (is (valid schema/item-schema node))
      (is (= (-> todo :org/status) :status/not-started))
      (is (= (-> todo :org/id) id))
      (is (= (-> todo :org/scheduled) date))
      (is (= (-> todo :org/links-to set) #{{:link/id link-id :link/text "clerk"}})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; tags
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest parses-tags
  (testing "parses tags"
    (let [node  (sut/parse-lines
                  (concat
                    ["#+TITLE: sample file"
                     "* [ ] todo with tags :some:tag:"
                     "* some misc note :post:idea:"]))
          items (->> node :org/items
                     (map (fn [n] [(:org/name n) n]))
                     (into {}))]
      (is (valid schema/item-schema node))
      (is (= (-> (items "todo with tags") :org/tags) #{"some" "tag"}))
      (is (= (-> (items "some misc note") :org/tags) #{"post" "idea"})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; flattned->nested
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest flattened->nested-test
  (testing "groups a sequence of passed items using their :level"
    (let [root-id (random-uuid)
          b-id    (random-uuid)
          node    (sut/parse-lines
                    (concat
                      (prop-bucket {:id root-id})
                      ["#+title: root"
                       "* a"
                       "* b"]
                      (prop-bucket {:id b-id})
                      ["** c"]
                      ["*** d"]
                      ["**** e"]
                      ["*** f"]
                      ["* g"]))]
      (is (valid schema/item-schema node))

      (doseq [item (-> node :org/items)]
        (let [level (-> item :org/level)]
          (is (= level 1))))

      (testing "sets parent ids"
        (let [items (:org/items node)
              a     (->> items (filter (comp #{"a"} :org/name)) first)
              b     (->> items (filter (comp #{"b"} :org/name)) first)
              g     (->> items (filter (comp #{"g"} :org/name)) first)]
          (is (= #{root-id} (:org/parent-ids a)))
          (is (= #{root-id} (:org/parent-ids b)))
          (is (= #{root-id} (:org/parent-ids g)))))

      (testing "sets relative-index name"
        (let [items (:org/items node)
              a     (->> items (filter (comp #{"a"} :org/name)) first)
              b     (->> items (filter (comp #{"b"} :org/name)) first)
              g     (->> items (filter (comp #{"g"} :org/name)) first)]
          (is (= 0 (:org/relative-index a)))
          (is (= 1 (:org/relative-index b)))
          (is (= 2 (:org/relative-index g)))))

      (let [c (-> node :org/items (nth 1)
                  :org/items first)]
        (is (= 2 (count (:org/items c))))

        (testing "sets parent ids"
          (is (= #{root-id b-id} (:org/parent-ids c))))

        (testing "sets relative-index name"
          (is (= 0 (:org/relative-index c))))
        (let [e (->> c :org/items
                     (filter (fn [{:keys [org/name]}]
                               (= name "d")))
                     first :org/items first)]
          (is (= "e" (:org/name e)))
          (testing "sets relative-index name"
            (is (= 0 (:org/relative-index e))))
          (testing "sets a parent name"
            (is (= "d > c > b > root" (:org/parent-name e))))))))

  (testing "doesnt drop strangely nested items"
    (let [root-id (random-uuid)
          b-id    (random-uuid)
          node    (sut/parse-lines
                    ["#+title: root"
                     "* a"
                     "***** b"
                     "** c"])]
      (is (valid schema/item-schema node))

      (let [items (:org/items node)
            a     (->> items (filter (comp #{"a"} :org/name)) first)]
        (is (= (count items) 1))
        (let [bandc (:org/items a)
              b     (->> bandc (filter (comp #{"b"} :org/name)) first)
              c     (->> bandc (filter (comp #{"c"} :org/name)) first)]
          (is (not (nil? b)))
          ;; TODO where's my item!!! fix this item
          #_(is (not (nil? c))))))))
