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
                  (fn [parent item]
                    (update parent :org/items conj item))
                  [{:org/level 1 :org/name "b"}
                   {:org/level 1 :org/name "a"}
                   {:org/level 2 :org/name "c"}
                   {:org/level 3 :org/name "d"}
                   {:org/level 4 :org/name "e"}
                   {:org/level 3 :org/name "f"}
                   {:org/level 1 :org/name "g"}])]
      (is (seq items))
      (is (= 3 (count items)))
      (doseq [item items]
        (let [level (-> item :org/level)]
          (is (= level 1))))
      (let [c (-> (nth items 1)
                  :org/items first)]
        (is (= 2 (count (:org/items c))))
        (is (->> c :org/items
                 (filter (fn [{:keys [org/name]}]
                           (= name "d")))
                 first :org/items first :org/name (= "e")))))))

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
