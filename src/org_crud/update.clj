(ns org-crud.update
  (:require
   [clojure.string :as string]
   [clojure.set :as set]
   [me.raynes.fs :as fs]
   [org-crud.core :as org]
   [org-crud.util :as util]
   [org-crud.headline :as headline]))

(defn ^:dynamic *item->org-path* [& _] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; item -> org lines
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn status->status-text [status]
  (when status
    (case status
      :status/cancelled   "CANCELLED"
      :status/done        "[X]"
      :status/not-started "[ ]"
      :status/in-progress "[-]")))

(defn append-tags [line tags]
  (if-not tags
    line
    (let [tags
          (if (coll? tags) (set tags) (set [tags]))]
      (str line
           (when (seq tags)
             (str " :"
                  (string/join ":" tags)
                  ":"))))))

(defn new-property-text [key value]
  (str (string/lower-case key) ": " value))

(defn prop->new-property [[k val]]
  (if (coll? val)
    (map-indexed (fn [i v]
                   (new-property-text (str k (when (> i 0) "+")) v)) val)
    (new-property-text k val)))

(defn new-property-bucket [props]
  (let [res
        (flatten
          (seq [":PROPERTIES:"
                (sort (flatten (map prop->new-property props)))
                ":END:"]))]
    res))

(defn body->lines [body]
  (reduce
    (fn [agg line]
      (cond
        ;; includes blank lines
        ;; also writes scheduled lines
        (:text line)
        (conj agg (:text line))

        (and (= :block (:type line))
             (= "SRC" (:block-type line)))
        (apply conj agg (flatten [(str "#+BEGIN_SRC " (:qualifier line))
                                  (map :text (:content line))
                                  "#+END_SRC"]))

        (and (= :drawer (:type line))
             (= :property-drawer-item (some-> line :content first :line-type)))
        ;; skip property drawers, they are handled elsewhere
        ;; could write these here, but i like them coming from `props` as a map
        agg

        :else
        (do
          (println "unhandled line in item->lines/body->lines" line)
          agg)))
    []
    body))

(defn item->lines
  ([item] (item->lines item (:level item)))
  ([{:keys [name props tags body status items]} level]
   (if (= :root level)
     (body->lines body)
     (let [level          (or level 1)
           level-str      (apply str (repeat level "*"))
           headline       (str level-str
                               (when status
                                 (str " " (status->status-text status)))
                               " " name)
           headline       (append-tags headline tags)
           prop-lines     (new-property-bucket props)
           body-lines     (body->lines body)
           children-lines (->> items
                               (mapcat item->lines))]
       (concat
         (conj
           (concat prop-lines body-lines)
           headline)
         children-lines)))))

(comment
  (item->lines {:name "hi" :tags ["next"] :props {:hi :bye}} 3))

(defn append-to-file!
  [path lines]
  (let [as-str (string/join "\n" lines)]
    (spit path (str "\n\n" as-str) :append true)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Tag Updates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-remove-tag?
  "Returns true if the passed val is a [:remove \"tag\"] val"
  [v]
  (when (and v (coll? v))
    (some-> v first (= :remove))))

(defn tag-to-remove
  "Returns true if the passed val is a [:remove \"tag\"] val"
  [v]
  (when (and v (coll? v))
    (some-> v second)))

(defn update-tags
  [current-tags tags-update]
  (let [to-remove (if (is-remove-tag? tags-update)
                    (set [(tag-to-remove tags-update)])
                    #{})

        tags (if (is-remove-tag? tags-update)
               #{}
               (if (coll? tags-update) (set tags-update) (set [tags-update])))

        tags (set/union current-tags tags)]
    (set/difference tags to-remove)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Prop Updates
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn is-remove?
  "Returns true if the passed map-val is a remove signal."
  [[_k v]]
  (when (and v (coll? v))
    (some-> v first (= :remove))))

(defn updated-props [old-props props-update]
  (let [remove-props (->> props-update
                          (filter is-remove?)
                          (into {}))
        props-update (remove is-remove? props-update)
        merged-props (util/merge-maps-with-multi
                       headline/*multi-prop-keys* old-props props-update)
        merged-props (map (fn [[k vs]]
                            (if-let [remove-signal (get remove-props k)]
                              [k (let [to-remove (second remove-signal)]
                                   (remove #(= to-remove %) vs))]
                              [k vs])) merged-props)
        merged-props (map
                       (fn [[k v]]
                         (if (coll? v)
                           [k (sort (set v))]
                           [k v]))
                       merged-props)
        merged-props (remove (comp nil? second) merged-props)]
    merged-props))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update fn helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-update
  "Updates the passed item based on the `up`date passed"
  [item up]
  (cond-> item
    (:status up)
    (assoc :status (:status up))

    (seq (:tags up))
    (update :tags update-tags (:tags up))

    (:add-item up)
    (update :items (fn [items]
                     (conj items (-> (:add-item up)
                                     (assoc :level
                                            (+ 1 (:level item)))))))

    (:name up)
    (assoc :name (:name up))

    (seq (:props up))
    (update :props updated-props (:props up))))

(defn matching-items? [it item]
  (or
    (and (:id it)
         (= (:id it) (:id item)))
    ;; try to match on name if no id
    (and
      (not (:id it))
      (not (:id item))
      (:name it)
      (= (:name it) (:name item)))))

(defn update-items
  "Traverses the `parsed` org structure, applying `update` to a child that
  matches item. Returns the updated org structure."
  [parsed item up]
  (let [is-delete? (= :delete-item up)]
    (->> parsed
         (reduce
           (fn [agg it]
             (if is-delete?
               (if (matching-items? it item)
                 ;; don't conj here to remove the item
                 (assoc agg :deleting? true)
                 (if (and (:deleting? agg)
                          (> (:level it)
                             (:level item)))
                   ;; don't conj here to remove nested items
                   agg
                   ;; remove deleting?, appending the rest
                   (-> agg
                       (update :items conj it)
                       (dissoc :deleting?))))

               (update agg :items
                       conj (if (matching-items? it item)
                              (apply-update it up)
                              it))))
           {:items []})
         :items
         (remove nil?))))

(defn write-updated
  "Writes the passed org-structure to disk as a .org file.
  Clears whatever was in the file before writing."
  [path items]
  (when path
    (let [lines  (reduce (fn [acc item]
                           (concat acc (item->lines item))) [] items)
          as-str (string/join "\n" lines)]
      (spit path as-str))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Update function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update!
  ([item up] (update! (*item->org-path* item) item up))
  ([path item up]
   (println "Updating props TWO"
            {:path      path         :item-name (:name item)
             :item-type (:type item) :update    up})
   (let [parsed-items (org/path->flattened-items path)
         updated      (update-items parsed-items item up)]
     (write-updated path updated))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public add function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn append-top-level
  [path item]
  (let [lines (item->lines
                (update item :props
                        (fn [props]
                          (merge {:added-at (util/now)} props))) 1)]
    (append-to-file! path lines)))

(defn add-to-context
  [path item context]
  (println (dissoc context :org-section))
  (update! path context
           {:add-item
            (update item :props
                    (fn [props]
                      (merge {:added-at (util/now)} props)))}))

(defn add-to-file!
  "Adds an item as an org headline to the indicated filepath.
  `context` indicates where to put the item.
  If `context` is `:top-level`, the item is added as the last top level in the file.
  "
  [path item context]
  (cond
    (= :top-level context)
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
                   (*item->org-path* context)
                   (*item->org-path* item))]
    (if (fs/file? org-path)
      (add-to-file! org-path item context)
      (println "Item add attempted for bad org-path" {:org-path org-path
                                                      :item     item}))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Delete items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn delete-from-file!
  "Deletes the item passed, if a match is found in the path"
  [path item]
  (update! path item :delete-item))

(defn delete-item!
  "Deletes the item passed, if a match is found in the path"
  [item]
  (let [org-path (*item->org-path* item)]
    (if (fs/file? org-path)
      (delete-from-file! org-path item)
      (do
        (println item)
        (println "Item delete attempted for bad org-path" org-path)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Refile items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn refile-within-file! [path item context]
  (println "Refiling item within file"
           {:file path :item item :context context})
  (delete-from-file! path item)
  (add-to-file! path item context))

(defn refile! [item context]
  (println "Refiling item to context" {:item item :context context})
  (delete-item! item)
  (add-item! item context))
