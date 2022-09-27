(ns org-crud.core
  (:require
   [clojure.walk :as walk]
   [clojure.string :as string]
   [babashka.fs :as fs]
   [org-crud.parse :as parse]
   [org-crud.node :as node]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; 'nesting' an ordered, flattened list of parsed org nodes
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn add-node-to-parent
  "Returns `parent` with `node` attached.

  Updates `node` with some `parent` and `parent-stack` specific details."
  [node parent parent-stack]
  (let [item (-> node
                 ;; set any parent ids
                 (assoc :org/parent-ids (->> parent-stack (map :org/id) (remove nil?) (into #{})))
                 ;; set a nested parent name
                 (assoc :org/parent-name (->> parent-stack
                                              (map (fn [parent]
                                                     (or (:org/name parent)
                                                         (:org/source-file parent))))
                                              (remove nil?)
                                              (string/join " > ")))
                 (assoc :org/parent-names (->> parent-stack
                                               (map (fn [parent]
                                                      (or (:org/name parent)
                                                          (:org/source-file parent))))
                                               (remove nil?)
                                               (into []))))]
    (-> parent (update :org/items conj item))))

(defn flattened->nested
  "Returns top-level items with sub-items as children"
  [xs]
  (->>
    (loop [remaining  xs
           items      []
           ctx-stack  []
           last-level 0]
      (let [{:org/keys [level] :as current-node} (some-> remaining first)
            level                                (if (#{:level/root} level) 0 level)
            level-diff                           (when current-node (- level last-level))]
        (if (and current-node (> level-diff 0))
          ;; we moved in -> at least 1 level, push current node and recur with new level
          (recur (rest remaining) items (conj ctx-stack current-node) level)

          ;; reduce over the level-diff or ctx-stack
          (let [{:keys [ctx-stack items]}
                (reduce
                  (fn [{:keys [ctx-stack items]} _]
                    (let [top-of-stack (peek ctx-stack)
                          ;; remove top from stack
                          ctx-stack    (when top-of-stack (pop ctx-stack))

                          ;; add top-of-stack to parent items
                          parent    (when (seq ctx-stack)
                                      (some-> ctx-stack peek
                                              ((fn [parent]
                                                 ;; update the item with the parent/stack context
                                                 (add-node-to-parent top-of-stack parent ctx-stack)))))
                          ;; replace parent in stack with updated parent
                          ctx-stack (when parent
                                      (-> ctx-stack pop (conj parent)))

                          ;; update items
                          items (cond
                                  ;; if we have a parent, don't touch items
                                  ;; (we already added top-o-s to the parent items)
                                  parent items

                                  ;; otherwise, add top-of-stack to items (presumably top-level)
                                  top-of-stack (conj items top-of-stack)

                                  :else items)]
                      {:ctx-stack ctx-stack
                       :items     items}))
                  {:ctx-stack ctx-stack
                   :items     items}
                  (if current-node
                    ;; pop as many times as diff in level
                    (range level-diff 1)
                    ;; pop as many times as diff in level
                    ctx-stack))]
            (if current-node
              ;; recur with updated
              (recur (rest remaining) items (conj ctx-stack current-node) level)
              ;; if no current-node (no `remaining`), return items
              items)))))

    ;; walk the created items to include some final node updates
    (walk/postwalk
      (fn [node]
        (if (not (and (map? node) (seq (:org/items node))))
          node
          (-> node
              (update :org/items
                      (fn [items]
                        (->> items
                             ;; TODO get this order right on the first pass?
                             reverse

                             ;; attach relative indexes
                             (map-indexed vector)
                             (map (fn [[i item]]
                                    (-> item (assoc :org/relative-index i)))))))))))))

(comment
  (flattened->nested
    [{:org/level :level/root :org/name "root"}
     {:org/level 1 :org/name "b"}
     {:org/level 1 :org/name "a"}
     {:org/level 2 :org/name "c"}
     {:org/level 3 :org/name "d"}
     {:org/level 4 :org/name "e"}
     {:org/level 2 :org/name "f"}
     {:org/level 1 :org/name "g"}]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing nested items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parsed->nested-items
  "Parses level-1 items with sub-sections as children"
  [source-file parsed]
  (when (and source-file parsed)
    (->> parsed
         (map (fn [raw] (node/->item raw source-file)))
         flattened->nested)))

(defn path->nested-item
  "Produces a single item, the :root of the org file.
  Level-1 sections can be found as :items, all children are nested as
  further lists of :items all the way down.

  If you want the org file as a flattened list, see `path->flattened-items`.
  "
  [p]
  (some->> p parse/parse-file (parsed->nested-items p) (remove nil?) first))

(defn dir->nested-items
  "For now, :recursive? goes one layer down.

  TODO impl with loop to recur properly."
  ([dir] (dir->nested-items {} dir))
  ([opts dir]
   (->> dir
        fs/list-dir
        (map (fn [f]
               (if (and (:recursive? opts)
                        (not (string/includes? f ".git"))
                        (fs/directory? f))
                 (fs/list-dir f)
                 [f])))
        (apply concat)
        (filter #(contains? #{".org"} (fs/extension %)))
        (map path->nested-item)
        (remove nil?))))

(comment
  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      count)
  (-> (dir->nested-items {:recursive? true} "/home/russ/Dropbox/notes")
      count)

  (-> (dir->nested-items "/home/russ/Dropbox/notes")
      first))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Parsing flattened items
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn path->flattened-items
  "Returns a flattened list of org items in the passed file.

  Produces flattened items, rather than nested.
  This means deeper org nodes will not be contained within parents.
  See `path->nested-items` for a single object with nested items.

  This function runs `path->nested-items`, then flattens the items for you.
  This is to ensure fields available in the flattened->nested path are set
  (:org/parent-name, :org/parent-ids, :org/relative-index).
  "
  [p]
  (tree-seq (comp seq :org/items) :org/items (path->nested-item p)))

(comment
  (let [p "/Users/russ/todo/projects.org"]
    ;; (some->> p parse-org-file (parsed->nested-items p) (remove nil?) first)
    (path->flattened-items p)))
