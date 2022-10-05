(ns org-crud.core-test
  (:require
   [org-crud.core :as sut]
   [clojure.test :refer [deftest testing is]]
   [babashka.fs :as fs]
   [clojure.string :as string]
   [clojure.set :as set]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks and fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def core-test-filepath
  (str (fs/cwd) "/test/org_crud/core-test.org"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing basic items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest path->flattened-items-test
  (let [items (sut/path->flattened-items core-test-filepath)]
    (testing "Parses an org file into a list of items"
      (is (seq items))
      (doseq [item items]
        (is (not (nil? (:org/name item))))))))

(deftest path->nested-item-test
  (let [item (sut/path->nested-item core-test-filepath)]
    (testing "parses an org file into a nested org item (with children)"
      (let [level (-> item :org/level)]
        (is (= level :level/root))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; word count test
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest word-count-test
  (testing "nested word-count is calced and set on items"
    (let [nested (sut/path->nested-item core-test-filepath)]
      (is (= 5 (:org/word-count nested)))))

  (testing "flattened word-count is calced and set on items"
    (let [items (sut/path->flattened-items core-test-filepath)]
      (is (= 65 (reduce + 0 (map :org/word-count items)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; filtering private org items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest exclude-private-tagged-files
  (testing "file with filetags :private: should return nil"
    (let [private-file-path (str (fs/cwd) "/test/org_crud/file-with-private-tag.org")]
      ;; make sure file actually exists
      (is (slurp private-file-path))

      ;; but parsing it should return nil
      (is (nil? (sut/path->flattened-items private-file-path)))
      (is (nil? (sut/path->nested-item private-file-path)))

      ;; parsing with other exclusions also shouldn't allow this
      (is (nil? (sut/path->flattened-items
                  {:exclude-tags #{"some" "other" "tags"}}
                  private-file-path)))
      (is (nil? (sut/path->nested-item
                  {:exclude-tags #{"some" "other" "tags"}}
                  private-file-path))))))

(defn first-child-with-tag [item tag]
  (->> item :org/items (filter (comp #(% tag) :org/tags)) first))

(deftest exclude-private-tagged-items
  (testing "nested items filter out :private:"
    (let [item (sut/path->nested-item core-test-filepath)]
      (is (= 4 (-> item :org/items count)))
      ;; make sure this filter works as expected
      (is (= 4 (count (->> item :org/items
                           (filter (comp #(string/includes? % "header") :org/name))))))
      ;; make sure the 'private' header is not in here
      (is (= 0 (count (->> item :org/items
                           (filter (comp #(string/includes? % "private") :org/name))))))
      (let [test-tagged-item    (first-child-with-tag item "test")
            test-tagged-subitem (first-child-with-tag test-tagged-item "test")]
        (is test-tagged-item)
        (is (= 3 (-> test-tagged-item :org/items count)))
        (is (= 1 (-> test-tagged-subitem :org/items count)))
        (is (= "and a public child" (-> test-tagged-subitem :org/items first :org/name))))))

  (testing "flattened items filter out :private:"
    (let [items (sut/path->flattened-items core-test-filepath)]
      (is (= 17 (-> items count)))
      ;; make sure the 'private' headers are not here
      (is (= 0 (count (->> items (filter (comp #(string/includes? % "private") :org/name))))))
      ;; sanity check
      (is (nil? (seq (set/intersection #{"private"} (->> items (mapcat :org/tags) set)))))))

  (testing "additional :exclude-tags does not accidentally include :private:"
    (let [item (sut/path->nested-item {:exclude-tags #{"test"}} core-test-filepath)]
      (is (= 3 (-> item :org/items count))))

    (let [items (sut/path->flattened-items {:exclude-tags #{"test"}} core-test-filepath)]
      (is (= 12 (-> items count)))
      (is (= 0 (count (->> items (filter (comp #(string/includes? % "private") :org/name))))))

      (is (nil? (seq (set/intersection #{"private"} (->> items (mapcat :org/tags) set)))))

      (is (= 0 (count (->> items (filter (comp #(string/includes? % "test") :org/name))))))
      (is (nil? (seq (set/intersection #{"test"} (->> items (mapcat :org/tags) set))))))))
