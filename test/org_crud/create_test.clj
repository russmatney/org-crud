(ns org-crud.create-test
  (:require
   [org-crud.create :as sut]
   [clojure.test :refer [deftest testing is use-fixtures]]
   [me.raynes.fs :as fs]
   [org-crud.headline :as headline]
   [org-crud.core :as org]
   [clojure.string :as string]
   [org-crud.util :as util]))

(def some-path
  (str fs/*cwd* "/test/org_crud/some-create-item-path.org"))

(defn test-fixtures
  [f]
  (fs/copy
    (str fs/*cwd* "/test/org_crud/create-test-before.org")
    (str fs/*cwd* "/test/org_crud/create-test.org"))

  (fs/delete some-path)

  (binding [headline/*multi-prop-keys* #{:repo-ids}]
    (f)))

(use-fixtures :each test-fixtures)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; mocks, fixtures, helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def org-filepath
  (str (str fs/*cwd*) "/test/org_crud/create-test.org"))

(defn ->items [] (org/path->flattened-items org-filepath))
(defn ->item [pred-map] (util/get-one pred-map (->items)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; creating new headlines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def new-headline-name "" "new headline")

(defn ->new-headline []
  (->item {:name new-headline-name}))

(defn add-new-headline [item]
  (sut/add-to-file! org-filepath item :org/level-1))

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
  (testing "a new, nested headline is created"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (->item {:name "parent headline"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))

(deftest create-new-headline-nested-newlines
  (testing "a new, nested headline is placed in the right spot"
    (is (= nil (->new-headline)))
    (add-new-nested-headline {:name new-headline-name}
                             (->item {:name "parent with mid-line asterisk"}))
    (is (= new-headline-name (:name (->new-headline))))
    (is (= 2 (:level (->new-headline))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Create new root-level items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def some-item
  {:name        "your org headline"
   :source-file "sometodos.org"
   :id          "your-uuid"
   :tags        #{"my" "tags" "are" "good"}
   :props       {:hello   "world"
                 :goodbye "blue monday"}
   :body        []})

(defn add-new-file [item]
  (sut/add-to-file! some-path item :org/root))

(defn item-from-file [path]
  (org/path->nested-item path))

(deftest create-new-file
  (testing "a new, root org file is created"
    (is (not (fs/exists? some-path)))
    (add-new-file some-item)
    (let [fetched (item-from-file some-path)]
      (is (= (:name fetched) (:name some-item)))
      (is (= (:id fetched) (:id some-item)))
      (is (= (:props fetched) (assoc (:props some-item)
                                     :id (:id some-item)
                                     :roam-tags
                                     (string/join " " (:tags some-item))
                                     :title (:name some-item))))
      (is (= (:tags fetched) (:tags some-item))))))

