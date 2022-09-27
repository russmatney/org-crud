(ns org-crud.parse-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [org-crud.parse :as sut]))

(deftest parse-lines-test-basic
  (testing "basic parse-lines works"
    (let [s    "some line"
          node (first (sut/parse-lines [s]))]
      (is (= (:org/word-count node) 2))
      (is (= (:org/level node) :level/root))
      (is (= (:org/body-string node) s)))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; flattned->nested
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest flattened->nested-test
  (testing "groups a sequence of passed items using their :level"
    (let [items (sut/flattened->nested
                  [{:org/level :level/root :org/name "root" :org/id "root-id"}
                   {:org/level 1 :org/name "b"}
                   {:org/level 1 :org/name "a" :org/id "a-id"}
                   {:org/level 2 :org/name "c"}
                   {:org/level 3 :org/name "d"}
                   {:org/level 4 :org/name "e"}
                   {:org/level 3 :org/name "f"}
                   {:org/level 1 :org/name "g"}])]
      (is (seq items))
      (is (= 1 (count items)))
      (doseq [item (-> items first :org/items)]
        (let [level (-> item :org/level)]
          (is (= level 1))))

      (testing "sets parent ids"
        (let [items (:org/items (first items))
              a     (->> items (filter (comp #{"a"} :org/name)) first)
              b     (->> items (filter (comp #{"b"} :org/name)) first)
              g     (->> items (filter (comp #{"g"} :org/name)) first)]
          (is (= #{"root-id"} (:org/parent-ids a)))
          (is (= #{"root-id"} (:org/parent-ids b)))
          (is (= #{"root-id"} (:org/parent-ids g)))))

      (testing "sets relative-index name"
        (let [items (:org/items (first items))
              a     (->> items (filter (comp #{"a"} :org/name)) first)
              b     (->> items (filter (comp #{"b"} :org/name)) first)
              g     (->> items (filter (comp #{"g"} :org/name)) first)]
          (is (= 0 (:org/relative-index b)))
          (is (= 1 (:org/relative-index a)))
          (is (= 2 (:org/relative-index g)))))

      (let [c (-> items first :org/items (nth 1)
                  :org/items first)]
        (is (= 2 (count (:org/items c))))

        (testing "sets parent ids"
          (is (= #{"root-id" "a-id"} (:org/parent-ids c))))

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
            (is (= "d > c > a > root" (:org/parent-name e)))))))))
