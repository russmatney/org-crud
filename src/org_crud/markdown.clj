(ns org-crud.markdown
  (:require [clojure.string :as string]
            [org-crud.fs :as fs]))

(defn item->frontmatter [item]
  (let [name (:name item)]
    ["---" (str "title: " name) "---"]))

(defn item->content [item]
  (->> item :body (map :text) (remove nil?) seq))

(defn item->md-lines [item]
  (concat
    (item->frontmatter item)
    (item->content item)))

(defn item->md-filename [item]
  (-> item
      :source-file
      fs/base-name
      fs/split-ext
      first
      (str ".md")))

(defn item->md-item [item]
  {:filename (item->md-filename item)
   :lines    (item->md-lines item)})

(defn write-md-item [target-dir md-item]
  (spit (str target-dir "/" (:filename md-item))
        (->> md-item :lines (string/join "\n"))))
