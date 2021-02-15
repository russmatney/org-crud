(ns org-crud.update
  (:require
   [clojure.string :as string]
   [clojure.set :as set]
   [org-crud.core :as org]
   [org-crud.util :as util]
   [org-crud.lines :as lines]
   [org-crud.headline :as headline]))

(def ^:dynamic *item->source-file* nil)

(defn append-to-file!
  ([path lines]
   (let [as-str (string/join "\n" lines)]
     (spit path as-str :append true))))

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

(defn remove-key-ns [[k v]]
  [(keyword (name k)) v])
(defn add-key-ns [ns [k v]]
  [(keyword (str ns "/" (name k))) v])

(defn updated-props [old-props props-update]
  (let [old-props    (->> old-props (map remove-key-ns) (into {}))
        props-update (->> props-update (map remove-key-ns) (into {}))
        _            (println old-props)
        _            (println props-update)

        remove-props  (->> props-update
                           (filter is-remove?)
                           (into {}))
        props-update  (remove is-remove? props-update)
        merged-props  (util/merge-maps-with-multi
                        headline/*multi-prop-keys* old-props props-update)
        merged-props  (map (fn [[k vs]]
                             (if-let [remove-signal (get remove-props k)]
                               [k (let [to-remove (second remove-signal)]
                                    (remove #(= to-remove %) vs))]
                               [k vs])) merged-props)
        merged-props  (map
                        (fn [[k v]]
                          (if (coll? v)
                            [k (sort (set v))]
                            [k v]))
                        merged-props)
        merged-props  (remove (comp nil? second) merged-props)
        updated-props (->> merged-props
                           (map (partial add-key-ns "org.prop"))
                           (into {}))]
    updated-props))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Update fn helpers
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn apply-update
  "Updates the passed item based on the `up`date passed"
  [item up]
  (cond-> item
    (:org/status up)
    (assoc :org/status (:org/status up))

    (seq (:org/tags up))
    (update :org/tags update-tags (:org/tags up))

    (:add-item up)
    (update :org/items (fn [items]
                         (conj items (-> (:add-item up)
                                         (assoc :org/level
                                                (+ 1 (:org/level item)))))))

    (:org/name up)
    (assoc :org/name (:org/name up))

    (:org/id up)
    (assoc :org/id (:org/id up))

    (seq (util/ns-select-keys "org.prop" up))
    ((fn [item]
       (let [new-props (updated-props
                         (util/ns-select-keys "org.prop" item)
                         (util/ns-select-keys "org.prop" up))]
         (-> (util/ns-remove-keys "org.prop" item)
             (merge new-props)))))))

(defn matching-items? [it item]
  (or
    (and (:org/id it)
         (= (:org/id it) (:org/id item)))
    ;; try to match on name if no id
    (and
      ;; temp! allowing one to not have the id, so that ids can be set without some other treatment
      (or (not (:org/id it))
          (not (:org/id item)))
      (:org/name it)
      (= (:org/name it) (:org/name item)))))

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
                 (assoc agg :deleting? true)
                 (if (and (:deleting? agg)
                          (> (:org/level it)
                             (:org/level item)))
                   ;; don't conj when :deleting? to remove nested items
                   agg
                   ;; remove deleting?, appending the rest
                   (-> agg
                       (update :items conj it)
                       (dissoc :deleting?))))

               (update agg :items
                       conj (if (matching-items? it item)
                              (let [res (apply-update it up)]
                                res)
                              it))))
           {:items []})
         :items
         (remove nil?))))

(defn write-updated
  "Writes the passed org-structure to disk as a .org file.
  Clears whatever was in the file before writing."
  [path items]
  (when path
    (let [lines
          (reduce (fn [acc item]
                    (concat acc (lines/item->lines item))) [] items)
          as-str (string/join "\n" lines)]
      (spit path as-str))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Public Update function
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn update!
  "`item` is a previously fetched org-item to update.

  `up` is a map describing a minimal update to make to the item.
  ex: `{:org/name \"My New Item Name\"}`
  "
  ([item up] (update! (*item->source-file* item) item up))
  ([path item up]
   (println "Updating item"
            {:path             path
             :item-source-file (:org/source-file item)
             :item-name        (:org/name item)
             :update           up})
   (let [parsed-items (org/path->flattened-items path)
         updated      (update-items parsed-items item up)]
     (write-updated path updated))))

(defn update-path-with-fn!
  "item->up should construct an update based on the passed item.
  If item->up returns nil, no update will be performed for that item.

  (comment
    (-> (str *drafts-dir* \"/journal-2019-12.org\")
        (org-crud.update/update-all-with-fn!
          (fn [item]
            (when-not (-> item :props :id)
              {:props {:id (.toString (java.util.UUID/randomUUID))}})))))
  "
  [path item->up]
  (println "Updating items at path with f" {:path path})
  (let [parsed-items (org/path->flattened-items path)
        updated      (reduce
                       (fn [agg item]
                         (if-let [up (item->up item)]
                           (update-items agg item up)
                           agg))
                       parsed-items
                       parsed-items)]
    (write-updated path updated)))

(defn update-dir-with-fn!
  "item->up should construct an update based on the passed item.
  If item->up returns nil, no update will be performed for that item.
  "
  [opts item->up]
  (println "Updating paths in dir with item->up" opts)
  (let [root-items (org/dir->nested-items opts (:dir opts))]
    (->> root-items
         (map (fn [it]
                (when-let [up (item->up it)]
                  (update! (:org/source-file it) it up))))
         doall)))
