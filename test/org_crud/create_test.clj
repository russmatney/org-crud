(ns org-crud.create-test
  (:require
   [org-crud.create :as sut]
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
;; creating new headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def new-headline-name ""
  "new headline")

(defn ->new-headline []
  (get-headline {:name new-headline-name}))

(defn add-new-headline [item]
  (sut/add-to-file! org-filepath item :top-level))

(deftest create-new-headline
  (testing "a new, top-level headline is created"
    (is (= nil (->new-headline)))
    (add-new-headline {:name new-headline-name})
    (is (= new-headline-name (:name (->new-headline))))))

(deftest create-new-headline-tags
  (testing "sets tags"
    (is (= nil (->new-headline)))
    (add-new-headline {:name new-headline-name
                       :tags "hi"})
    (is (= #{"hi"} (:tags (->new-headline))))))

(deftest create-new-headline-props
  (testing "sets props"
    (is (= nil (->new-headline)))
    (add-new-headline {:name  new-headline-name
                       :tags  "hi"
                       :props {:hi "bye"}})
    (is (= "bye" (:hi (:props (->new-headline)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new nested headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-new-nested-headline [item context]
  (sut/add-to-file! org-filepath item context))

(deftest create-new-headline-nested
  (testing "a new, top-level headline is created"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (get-headline {:name "parent headline"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))

(deftest create-new-headline-nested-newlines
  (testing "a new, top-level headline is placed in the right spot"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (get-headline {:name
                                            "parent with mid-line asterisk"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))
