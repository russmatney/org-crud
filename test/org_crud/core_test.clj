(ns org-crud.core-test
  (:require [org-crud.core :as sut]
            [clojure.test :refer [deftest testing is]]
            [me.raynes.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks and fixtures
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/core-test.org"))

(defn ->items []
  (sut/path->items org-filepath))

(defn ->nested-items []
  (sut/path->items-with-nested org-filepath))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing basic items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest path->items-test
  (testing "Helpers parse org headlines into basic items"
    (let [items (->items)]
      (is (seq items))
      (doseq [item items]
        (is (not (nil? (:name item))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; parsing into a nested, heirarchical structure
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest path->items-with-nested-test
  (testing "parsed items include their children"
    (let [items (->nested-items)]
      ;; (doall
      ;;   (for [it items]
      ;;     (pprint/pprint
      ;;       (dissoc it :org-section :tags :raw-headline :props))))
      (is (seq items))
      (doseq [item items]
        (let [level (-> item :level)]
          (is (= level :root)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; flattned->nested
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest flattened->nested-test
  (testing "groups a sequence of passed items using their :level"
    (let [items (sut/flattened->nested
                  (fn [parent item]
                    (update parent :items conj item))
                  [{:level 1 :name "b"}
                   {:level 1 :name "a"}
                   {:level 2 :name "c"}
                   {:level 3 :name "d"}
                   {:level 4 :name "e"}
                   {:level 3 :name "f"}
                   {:level 1 :name "g"}])]
      (is (seq items))
      (is (= 3 (count items)))
      (doseq [item items]
        (let [level (-> item :level)]
          (is (= level 1))))
      (let [c (-> (nth items 1)
                  :items first)]
        (is (= 2 (count (:items c))))
        (is (->> c :items
                 (filter (fn [{:keys [name]}]
                           (= name "d")))
                 first :items first :name (= "e")))))))
