(ns org-crud.create
  (:require
   [org-crud.update :as up]
   [org-crud.fs :as fs]
   [org-crud.lines :as lines]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public add function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append-top-level
  [path item]
  (let [lines (up/item->lines
                (update item :props
                        (fn [props]
                          (merge
                            ;; TODO support via adapter/dynamism?
                            ;; {:added-at (util/now)}
                            props))) 1)]
    (up/append-to-file! path (concat ["\n\n"] lines))))

(defn add-to-context
  [path item context]
  (println (dissoc context :org-section))
  (up/update! path context
              {:add-item
               (update item :props
                       (fn [props]
                         (merge
                           ;; TODO support via adapter/dynamism?
                           ;; {:added-at (util/now)}
                           props)))}))

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
      (fs/touch path)
      (let [lines (lines/item->root-lines item)]
        (up/append-to-file! path lines)))))

(defn add-to-file!
  "Adds an item as an org headline to the indicated filepath.
  `context` indicates where to put the item.
  If `context` is `:top-level`, the item is added as the last top level in the file.
  "
  [path item context]
  (cond
    (= :org/root context)
    (create-root-file path item)

    (or
      (= :org/level-1 context)
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
  (let [org-path (if (map? context)
                   (up/*item->source-file* context)
                   (up/*item->source-file* item))]
    (if org-path
      (add-to-file! org-path item context)
      (println "Item add attempted for bad org-path" {:org-path org-path
                                                      :item     item}))))
