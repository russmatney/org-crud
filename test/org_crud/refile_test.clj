(ns org-crud.refile-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [babashka.fs :as fs]
   [org-crud.refile :as sut]
   [org-crud.util :as util]
   [org-crud.core :as org]))

(defn test-fixtures
  [f]
  (fs/copy
    (str (fs/cwd) "/test/org_crud/refile-test-before.org")
    (str (fs/cwd) "/test/org_crud/refile-test.org")
    {:replace-existing true})
  (f))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (fs/cwd) "/test/org_crud/refile-test.org"))

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
    (is (= to-refile (:org/name (get-headline {:org/name to-refile}))))
    (do-refile (get-headline {:org/name to-refile}) :level/level-1)
    (is (= 1 (:org/level (get-headline {:org/name to-refile}))))))

(deftest refile-headline-nested
  (testing "refiles a headline under the passed context"
    (is (= to-refile (:org/name (get-headline {:org/name to-refile}))))
    (do-refile (get-headline {:org/name to-refile})
               (get-headline {:org/name "target todo"}))
    (is (= 4 (:org/level (get-headline {:org/name to-refile}))))))

(deftest refile-headline-include-body
  (testing "refiles a headline, including it's body content"
    (is (= to-refile (:org/name (get-headline {:org/name to-refile}))))
    (let [body-count (count (:org/body (get-headline {:org/name to-refile})))]
      (do-refile (get-headline {:org/name to-refile})
                 (get-headline {:org/name "target todo"}))
      (is (not (nil? body-count)))
      (is (= body-count (count (:org/body (get-headline {:org/name to-refile}))))))))
