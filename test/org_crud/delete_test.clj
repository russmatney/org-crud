(ns org-crud.delete-test
  (:require [org-crud.delete :as sut]
            [clojure.test :refer [deftest testing is use-fixtures]]
            [me.raynes.fs :as fs]
            [org-crud.util :as util]
            [org-crud.core :as org]))

(defn test-fixtures
  [f]
  (fs/copy
    (str fs/*cwd* "/test/org_crud/delete-test-before.org")
    (str fs/*cwd* "/test/org_crud/delete-test.org"))
  (f))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/delete-test.org"))

(defn ->items []
  (org/path->flattened-items org-filepath))

(defn get-headline [pred-map]
  (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; delete a headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def deleted-headline-name ""
  "delete headline")

(def deleted-nested-headline-name ""
  "delete nested headline")

(defn ->deleted-headline []
  (get-headline {:org/name deleted-headline-name}))

(defn ->deleted-nested-headline []
  (get-headline {:org/name deleted-nested-headline-name}))

(defn do-delete-headline [item]
  (sut/delete-from-file! org-filepath item))

(deftest delete-headline
  (testing "deletes a headline"
    (is (= deleted-headline-name (:org/name (->deleted-headline))))
    (do-delete-headline (->deleted-headline))
    (is (= nil (->deleted-headline)))
    (is (= nil (get-headline {:org/name "deleted more content"})))))

(deftest delete-nested-headline
  (testing "deletes a nested headline"
    (is (= deleted-nested-headline-name (:org/name (->deleted-nested-headline))))
    (do-delete-headline (->deleted-nested-headline))
    (is (= nil (->deleted-nested-headline)))
    (is (not= nil (get-headline {:org/name "deleted more content"})))))
