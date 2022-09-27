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
;; word count test
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest word-count-test
  (testing "nested word-count is calced and set on items"
    (let [nested (->nested-item)]
      (is (= 5 (:org/word-count nested)))))

  (testing "flattened word-count is calced and set on items"
    (let [items (->items)]
      (is (= 49 (reduce + 0 (map :org/word-count items)))))))
