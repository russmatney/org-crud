(ns org-crud.parse
  (:require
   [clojure.walk :as walk]
   [clojure.string :as string]
   [clojure.java.io :as io]
   [babashka.fs :as fs]

   [organum.core :as organum.core]
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
;; Parse
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn parse-lines
  "Parses a seq of strings into a single org-crud node.
  Returns a single root node.

  Expects to be handed lines in order from a single file,
  but maybe be useful as a parser for org content in other situations.
  "
  ([lines] (parse-lines lines nil))
  ([lines path]
   (when (seq lines)
     (some->> lines
              (reduce #'organum.core/handle-line [(#'organum.core/root)])
              ;; TODO bake node/->item logic into the handle-line helpers
              (map #(node/->item % path))
              flattened->nested
              (remove nil?)
              first))))

(defn parse-file
  [path]
  (try
    (with-open [rdr (io/reader (-> path fs/absolutize str))]
      (parse-lines (line-seq rdr) path))
    (catch Exception ex
      (println "org-crud.parse/parse-file exception" path)
      (println ex)
      nil)))

(comment
  (parse-file (str (fs/home) "/todo/readme.org"))
  (parse-file (str (fs/home) "/todo/daily/2022-09-26.org"))
  (parse-file (str (fs/home) "/todo/journal.org")))
