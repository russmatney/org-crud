(ns org-crud.markdown-test
  (:require
   [org-crud.markdown :as sut]
   [clojure.test :refer [deftest testing is]]
   [org-crud.core :as org]
   [org-crud.fs :as fs]
   [clojure.string :as string]))

(def fixture-dir (str fs/*cwd* "/test/org_crud/markdown_fixtures"))

(defn build-md-files
  "Converts all the .org files in fixture-dir to .md files."
  []
  (doall
    (->> fixture-dir
         org/dir->nested-items
         (map (fn [item]
                {:filename (str (:name item) ".md")
                 :lines    (sut/item->md-lines item)}))
         (map (fn [file]
                (spit (str fixture-dir "/" (:filename file))
                      (->> file :lines (string/join "\n"))))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item->frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(deftest frontmatter-test
  (build-md-files)
  (let [files (fs/list-dir fixture-dir)]
    (testing "same number of org and md files"
      (is (= (->> files
                  (filter #(= (fs/extension %) ".org"))
                  count)
             (->> files
                  (filter #(= (fs/extension %) ".md"))
                  count))))))
