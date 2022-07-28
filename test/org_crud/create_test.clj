(ns org-crud.create-test
  (:require
   [org-crud.create :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [babashka.fs :as fs]
   [org-crud.core :as org]
   [org-crud.util :as util]))

(def some-path
  (str (fs/cwd) "/test/org_crud/some-create-item-path.org"))

(defn test-fixtures
  [f]
  (fs/copy
    (str (fs/cwd) "/test/org_crud/create-test-before.org")
    (str (fs/cwd) "/test/org_crud/create-test.org")
    {:replace-existing true})

  (when (fs/exists? some-path)
    (fs/delete some-path))

  (f))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (fs/cwd) "/test/org_crud/create-test.org"))

(defn ->items [] (org/path->flattened-items org-filepath))
(defn ->item [pred-map] (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def new-headline-name "" "new headline")

(defn ->new-headline []
  (->item {:org/name new-headline-name}))

(defn add-new-headline [item]
  (sut/add-to-file! org-filepath item :level/level-1))

(deftest create-new-headline
  (testing "a new, top-level headline is created"
    (is (= nil (->new-headline)))
    (add-new-headline {:org/name new-headline-name})
    (is (= new-headline-name (:org/name (->new-headline))))))

(deftest create-new-headline-tags
  (testing "sets tags"
    (is (= nil (->new-headline)))
    (add-new-headline {:org/name new-headline-name
                       :org/tags "hi"})
    (is (= #{"hi"} (:org/tags (->new-headline))))))

(deftest create-new-headline-props
  (testing "sets props"
    (is (= nil (->new-headline)))
    (add-new-headline {:org/name    new-headline-name
                       :org/tags    "hi"
                       :org.prop/hi "bye"})
    (is (= "bye" (:org.prop/hi (->new-headline))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new nested headline
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-new-nested-headline [item context]
  (sut/add-to-file! org-filepath item context))

(deftest create-new-headline-nested
  (testing "a new, nested headline is created"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:org/name new-headline-name}
                             (->item {:org/name "parent headline"}))
    (is (= new-headline-name (:org/name (->new-headline))))
    (is (= 2 (:org/level (->new-headline))))))

(deftest create-new-headline-nested-newlines
  (testing "a new, nested headline is placed in the right spot"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:org/name new-headline-name}
                             (->item {:org/name "parent with mid-line asterisk"}))
    (is (= new-headline-name (:org/name (->new-headline))))
    (is (= 2 (:org/level (->new-headline))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create new root-level items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def some-item
  {:org/name         "your org headline"
   :org/source-file  "sometodos.org"
   :org/id           "your-uuid"
   :org/tags         #{"my" "tags" "are" "good"}
   :org.prop/hello   "world"
   :org.prop/goodbye "blue monday"
   :org/body         []})

(defn add-new-file [item]
  (sut/add-to-file! some-path item :level/root))

(defn item-from-file [path]
  (org/path->nested-item path))

(deftest create-new-file
  (testing "a new, root org file is created"
    (is (not (fs/exists? some-path)))
    (add-new-file some-item)
    (let [fetched (item-from-file some-path)]
      (is (= (:org/name fetched) (:org/name some-item)))
      (is (= (:org/id fetched) (:org/id some-item)))
      (is (= (:org.prop/hello fetched) (:org.prop/hello some-item)))
      (is (= (:org.prop/goodbye fetched) (:org.prop/goodbye some-item)))
      (is (= (:org/tags fetched) (:org/tags some-item))))))
