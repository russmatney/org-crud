(ns org-crud.markdown
  (:require [clojure.string :as string]
            [org-crud.fs :as fs]))

(defn item->frontmatter [item]
  (let [name (:name item)]
    ["---" (str "title: " name) "---"]))

(defn body-line->md-lines [item]
  (cond
    (contains? #{:blank :table-row} (:line-type item))
    [(:text item)]

    (and (= :block (:type item))
         (= "SRC" (:block-type item)))
    (flatten [(str "``` " (:qualifier item))
              (map body-line->md-lines (:content item))
              "```"])))

(defn item->md-body [item]
  (let [child-lines (mapcat item->md-body (:items item))
        header-line
        (if (int? (:level item))
          (str (apply str (repeat (:level item) "#")) " " (:name item))
          "")
        body-lines  (->> item
                        :body
                        (remove #(= (:line-type %) :comment))
                        (mapcat body-line->md-lines)
                        (remove nil?)
                        seq)]
    (println "header" header-line)
    (println "body" body-lines)
    (println "child" child-lines)
    (concat [header-line] body-lines child-lines)))

(comment
  (str (apply str (repeat 2 "#")) " thats my name")
  )

(defn item->md-lines [item]
  (concat
    (item->frontmatter item)
    (item->md-body item)))

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
