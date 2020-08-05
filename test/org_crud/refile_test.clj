(ns org-crud.refile-test
  (:require [org-crud.refile :as sut]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [me.raynes.fs :as fs]
            [org-crud.headline :as headline]
            [org-crud.util :as util]
            [org-crud.core :as org]))

(defn test-fixtures
  [f]
  (fs/copy
    (str fs/*cwd* "/test/org_crud/create-test-before.org")
    (str fs/*cwd* "/test/org_crud/create-test.org"))
  (binding [headline/*multi-prop-keys* #{:repo-ids}]
    (f)))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/create-test.org"))

(defn ->items []
  (org/path->flattened-items org-filepath))

(defn get-headline [pred-map]
  (util/get-one pred-map (->items)))

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
