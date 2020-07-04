(ns org-crud.markdown
  (:require [clojure.string :as string]
            [org-crud.fs :as fs]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Frontmatter
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->frontmatter [item]
  (let [name (:name item)]
    ["---" (str "title: " name) "---"]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> markdown body
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn body-str->file-refs [s]
  (some->> s
           (re-seq #"\[\[file:([^\]]*)\]\[[^\]]*\]\]")
           (map second)))

(defn org-links->md-links
  "Rearranges org-links found in the string with the md style.
  The structure is a supported relative link usable with the gatsby-catch-links
  plugin.

  Works across line breaks, expects to be passed the entire joined file."
  [s]
  (string/replace
    s
    #"\[\[file:([^\]]*)\]\[([^\]]*)\]\]"
    (fn [res]
      (let [file-path (some->> res (drop 1) first fs/base-name fs/split-ext first)
            link-text (some->> res (drop 2) first)]
        (when (and file-path link-text)
          (str "[" link-text "](/" file-path ")")))
      )))

(comment
  (string/replace "hello" #"l(.*)" #(str "hi" %1))

  (re-seq
    #"\[\[file:([^\]]*)\]\[([^\]]*)\]\]"
    "Wide net for [[file:20200609220548-capture_should_be_easy.org][easy capture]]")

  (org-links->md-links
    "Wide net for [[file:20200609220548-capture_should_be_easy.org][easy \n  capture]]
\n
    and other things too [[file:20200609220548-other-things-too.org][something \n  capture]]
"
    )

  (string/replace
    "Wide net for [[file:20200609220548-capture_should_be_easy.org][easy \n  capture]]"
    #"\[\[file:([^\]]*)\]\[([^\]]*)\]\]"
    (fn [res]
      (let [file-path (some->> res (drop 1) first fs/base-name fs/split-ext first)
            link-text (some->> res (drop 2) first)]
        (when (and file-path link-text)
          (str "[" link-text "](/" file-path ")")))
      ))
  )

(defn body-line->md-lines [line]
  (cond
    (contains? #{:blank :table-row :unordered-list} (:line-type line))
    [(:text line)]

    (and (= :block (:type line))
         (= "SRC" (:block-type line)))
    (flatten [(str "``` " (:qualifier line))
              (map body-line->md-lines (:content line))
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
    (reduce
      (fn [lines line]
        (conj lines line))
      []
      (concat [header-line] body-lines child-lines))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> markdown
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

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

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public: converting to and writing a markdown file
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn item->md-item [item]
  {:filename (item->md-filename item)
   :lines    (item->md-lines item)})

(defn write-md-item [target-dir md-item]
  (spit (str target-dir "/" (:filename md-item))
        (->> md-item :lines (string/join "\n"))))
