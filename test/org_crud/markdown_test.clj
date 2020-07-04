(ns org-crud.markdown-test
  (:require
   [org-crud.markdown :as sut]
   [clojure.test :refer [deftest testing is]]
   [org-crud.core :as org]
   [org-crud.fs :as fs]))

(def fixture-dir (str fs/*cwd* "/test/org_crud/markdown_fixtures"))

(defn parsed-org-files []
  (org/dir->nested-items fixture-dir))

(defn parsed-org-file [fname]
  (org/path->nested-item (str fixture-dir "/" fname)))

(defn build-md-files
  "Converts all the .org files in fixture-dir to .md files."
  []
  (doall
    (->> (parsed-org-files)
         (map sut/item->md-item)
         (map (partial sut/write-md-item fixture-dir)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; general
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest build-and-write-count-test
  (build-md-files)
  (let [files (fs/list-dir fixture-dir)]
    (testing "same number of org and md files"
      (is (> (->> files (filter #(= (fs/extension %) ".org")) count) 0))
      (is (= (->> files (filter #(= (fs/extension %) ".org")) count)
             (->> files (filter #(= (fs/extension %) ".md")) count))))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest frontmatter-test
  (let [example-org (parsed-org-file "example.org")
        lines       (-> example-org sut/item->frontmatter)]
    (testing "org items convert to a proper frontmatter"
      (is (= "---" (first lines)))
      (is (= "---" (last lines))))))
