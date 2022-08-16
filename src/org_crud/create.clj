(ns org-crud.create
  (:require
   [babashka.fs :as fs]
   [clojure.string :as string]
   [org-crud.update :as up]
   [org-crud.lines :as lines]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public add function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append-top-level
  [path item]
  (let [lines (lines/item->lines {:override-level 1} item)]
    (up/append-to-file! path (concat ["\n\n"] lines))))

(defn add-to-context
  [path item context]
  (println (dissoc context :org-section))
  (up/update! path context {:add-item item}))

(defn create-root-file
  "Creates a new file at the passed `path` treating the item as a root-level
  org-item.

  Properties are converted to file props.

  TBD overwriting existing files
  "
  [path item]
  (if (fs/exists? path)
    (println "Refusing create, file exists.")
    (do
      (println "Creating file for root item" path item)
      (fs/create-file path)
      (let [lines (lines/item->root-lines item)]
        (up/append-to-file! path lines)))))

(defn slugify [item]
  (-> item
      :org/name
      (string/replace #"\s" "_")
      (string/replace #"[^A-Za-z0-9]" "_")
      (string/replace #"__*" "_")
      (string/replace #"^_" "")
      (string/replace #"_$" "")
      string/lower-case))

(comment
  (slugify {:org/name "This wasn't ____ a tasknam&*e8989"}))

(defn create-roam-file
  "Creates a new roam file in the passed `dir`.
  The roam file path created is opinionated but easily abstracted.
  "
  [dir-path item]
  (when (and (fs/exists? dir-path)
             (seq (:org/name item)))
    (let [slug     (slugify item)
          dir-name (str dir-path "/" slug ".org")]
      (create-root-file dir-name item))))


(defn add-to-file!
  "Adds an item as an org node to the indicated filepath.
  `context` indicates where to put the item.
  If `context` is `:top-level`, the item is added as the last top level in the file.
  "
  [path item context]
  (cond
    (or
      (= :org/root context)
      (= :level/root context))
    (create-root-file path item)

    (or
      (= :org/level-1 context)
      (= :level/level-1 context)
      (= :top-level context))
    (append-top-level path item)

    (map? context)
    (add-to-context path item context)

    :else
    (println "Unsupported add! context! " context)))

(defn add-item!
  "Adds a passed item to the passed context.
  Uses the context to find the org-path, falling back
  to the item if the context is :top-level."
  [item context]
  (let [org-path (:org/source-path (if (map? context) context item))]
    (if org-path
      (add-to-file! org-path item context)
      (println "Item add attempted for bad org-path" {:org-path org-path
                                                      :item     item}))))
