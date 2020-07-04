(ns org-crud.markdown)

(defn item->frontmatter [item]
  (let [name (:name item)]
    ["---" (str "title: " name) "---"]))

(defn item->content [item]
  (->> item :body (map :text) (remove nil?) seq))

(defn item->md-lines [item]
  (concat
    (item->frontmatter item)
    (item->content item)))
