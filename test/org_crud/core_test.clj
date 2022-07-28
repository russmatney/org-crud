(ns org-crud.core-test
  (:require
   [org-crud.core :as sut]
   [clojure.test :refer [deftest testing is]]
   [babashka.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks and fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (fs/cwd) "/test/org_crud/core-test.org"))

(defn ->items []
  (sut/path->flattened-items org-filepath))

(defn ->nested-item []
  (sut/path->nested-item org-filepath))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing basic items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest path->flattened-items-test
  (testing "Helpers parse org nodes into basic items"
    (let [items (->items)]
      (is (seq items))
      (doseq [item items]
        (is (not (nil? (:org/name item))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing into a nested, heirarchical structure
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest path->nested-item-test
  (testing "parsed items include their children"
    (let [item  (->nested-item)
          level (-> item :org/level)]
      (is (= level :level/root)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; flattned->nested
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest flattened->nested-test
  (testing "groups a sequence of passed items using their :level"
    (let [items (sut/flattened->nested
                  [{:org/level :level/root :org/name "root"}
                   {:org/level 1 :org/name "b"}
                   {:org/level 1 :org/name "a"}
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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; word count test
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest word-count-test
  (testing "nested word-count is calced and set on items"
    (let [nested (->nested-item)]
      (is (= 5 (:org/word-count nested)))))

  (testing "flattened word-count is calced and set on items"
    (let [items (->items)]
      (is (= 49 (reduce + 0 (map :org/word-count items)))))))
