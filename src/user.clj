(ns user
  (:require
   [org-crud.update :as upd]
   [org-crud.core :as org]
   [wing.repl :as w.repl]
   #_[clomacs :as clomacs]
   [clojure.string :as string]
   [babashka.fs :as fs]
   [babashka.process :as process]
   [org-crud.update :as update]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Ensuring uuids on all nodes

(defn ensure-id [item]
  (let [id
        (or (:org/id item)
            (:org/props.id item)
            (str (java.util.UUID/randomUUID)))]
    (-> item
        (assoc :org/id id)
        (assoc :org/props.id id))))

(comment
  (def --roam-dir "/home/russ/Dropbox/notes")

  (org/dir->nested-items --roam-dir)
  (upd/update-dir-with-fn! {:dir        --roam-dir
                            :recursive? true} ensure-id)
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; renaming org-roam files to move timestamps into property drawers

(defn org->starts-with-datetime? [org]
  ((comp (fn [fname]
           (or
             (string/starts-with? fname "2020")
             (string/starts-with? fname "2021")))
         fs/file-name :org/source-file)
   org))

(defn fname->data [s]
  (let [groups
        (->>
          (re-seq #"(\d\d\d\d)(\d\d)(\d\d)(\d\d)(\d\d)(\d\d)-([\w_.]+)" s)
          first
          (drop 1))
        new-fname                     (last groups)
        [year month day hour min sec] (->> groups (take 6) (into []))]
    {:new-filename new-fname
     :created-at   (str year month day ":" hour min sec)}))

(comment
  (def --dir "/home/russ/Dropbox/todo/garden")

  (def --ex-str "20201228124352-readme_idioms.org")

  (fname->data --ex-str)

  (->>
    (org/dir->nested-items --dir)
    (filter org->starts-with-datetime?)
    ;; (map (comp fs/file-name :org/source-file))
    (map (comp :org/source-file))
    ;; (take 3)
    count
    )

  (->>
    (org/dir->nested-items --dir)
    (filter org->starts-with-datetime?)
    ;; (take 2)
    (map (fn [org]
           (let [
                 {:keys [new-filename created-at]} ((comp fname->data fs/file-name :org/source-file) org)
                 file-dir                          (-> org :org/source-file (#(string/replace % (fs/file-name %) "")))
                 new-filepath                      (str file-dir new-filename)]
             ;; (println "new-filename" new-filename)
             ;; (println "filedir" file-dir)
             ;; (println "new-filepath" new-filepath)

             ;; update props
             (upd/update! (:org/source-file org) org {:org.prop/created-at created-at})

             ;; move file
             (->
               ^{:out :string
                 :dir file-dir}
               (process/$ mv ~(-> org :org/source-file) ~new-filepath)
               process/check
               :out)))))
  )


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; misc

(comment
  (w.repl/sync-libs!)


  (defn missing-id [item]
    (and (not (:id item))
         (not (-> item :props :id))))

  (upd/update-dir-with-fn!
    {:dir --roam-dir}
    (fn [item]
      (when (missing-id item)
        (println "missing id!" (:source-file item))
        (let [id (str (java.util.UUID/randomUUID))]
          {:id id :props {:id id}}))))


  ;; (clomacs/clomacs-defn
  ;;   emacs-version
  ;;   emacs-version)

  ;; (println (emacs-version))

  ;; (clomacs/clomacs-defn
  ;;   clmx-current-org-item
  ;;   clmx-current-org-item)

  ;; (println (clmx-current-org-item))

  )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; merge org-roam files


(defn find-replace-in-org-dir [{:keys [old-id new-id dry-run]}]
  (let [dry-run
        ;; default to dry-run, require opt-out
        (if (= dry-run false) false true)]
    (println "\n\n\tFind-replacing across your org files!!")
    (if dry-run (println "\tDRY RUN!") (println "\tBOMBS AWAY!"))

    (let [files
          (concat
            (fs/list-dir (fs/expand-home "~/todo/garden") "*.org")
            (fs/list-dir (fs/expand-home "~/todo/daily") "*.org")
            (fs/list-dir (fs/expand-home "~/todo/garden/workspaces") "*.org"))]
      (->> files
           #_(take 50)
           (map (fn [file]
                  (let [lm           (fs/last-modified-time file)
                        any-changes? (atom false)
                        res
                        (cond->> (slurp (str file))
                          true (string/split-lines)
                          true
                          (map
                            (fn [line]
                              (if (string/includes? line old-id)
                                (do
                                  (when dry-run
                                    (println "\n\t" (fs/file-name file)
                                             "\nwould replace" line
                                             "\nwith" (string/replace line old-id new-id)))

                                  (when-not @any-changes?
                                    (reset! any-changes? true))

                                  (when-not dry-run
                                    (println "\n\t updating" (fs/file-name file) "\n" line
                                             "\nwith" (string/replace line old-id new-id)))

                                  (if dry-run
                                    line (string/replace line old-id new-id)))
                                line)))
                          true                (string/join "\n")
                          (and (not dry-run)
                               @any-changes?) (spit (str file)))]
                    (fs/set-last-modified-time file lm)
                    res)))
           (remove nil?)
           count))))

(comment
  (find-replace-in-org-dir
    {:old-id  "id:df00a174-36ab-42de-92ef-2ec5b0d7bf03"
     :new-id  "id:f617ee24-cfad-4c6e-8dff-227ae56c22e5"
     :dry-run true})

  (->>
    (fs/list-dir (fs/expand-home "~/todo/garden/workspaces") "*.org")
    (take 1)
    (map str)
    (map org-crud.core/path->nested-item)
    (map
      (fn [{:keys [org/id org/short-path org/source-file] :as item}]
        ;; could just copy the file over via `cp`
        ;; no need to get this fancy
        (when id
          (println short-path "id: " id)
          (let [dest-file (-> source-file (string/replace "/workspaces" ""))
                new-item  (-> item
                              (dissoc :org/id :org.prop/id)
                              ensure-id ;; get a new uuid
                              (assoc :org/source-file dest-file))

                new-id (if (fs/exists? (:org/source-file new-item))
                         (->
                           new-item
                           :org/source-file
                           org-crud.core/path->nested-item
                           :org/id)
                         (:org/id new-item))]
            (println dest-file "new-id: " new-id)
            (if (fs/exists? (:org/source-file new-item))
              (println "matching garden file exists! maybe we should merge?")
              (do
                (println "copying over existing content")
                (org-crud.update/update! item new-item)))

            (when (and id new-id)
              (find-replace-in-org-dir {:old-id  (str "id:" id)
                                        :new-id  (str "id:" new-id)
                                        :dry-run false}))))))
    doall)
  )
