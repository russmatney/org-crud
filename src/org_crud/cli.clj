(ns org-crud.cli
  (:require
   [clojure.string :as string]
   [clojure.tools.cli :refer [parse-opts]]
   [org-crud.markdown :as markdown]
   [org-crud.agenda :as agenda]
   [org-crud.counts :as counts]
   ))

(def org-to-markdown-cli-opts
  [["-b" "--blog-type TYPE" "Blog Type (jekyll or gatsby)"
    :default "jekyll"
    :validate [(fn [t]
                 (#{"jekyll" "gatsby"} t)) "Must be either 'jekyll' or 'gatsby'"]]
   ["-t" "--tags TAGS" "Comma-seped tags to include in all posts. ('-t note,roam')"
    :default nil
    :parse-fn (fn [s] (->> (string/split s #",") (into #{})))]
   ])

(defn org-to-markdown [& args]
  (let [{:keys [arguments options summary errors]}
        (parse-opts args org-to-markdown-cli-opts)]
    (if (or errors (not (= (count arguments) 2)))
      (do
        (println "Expected call format: `bb org-crud.jar org-to-markdown <src-dir> <out-dir> (options)`")
        (println (str "\n" summary "\n\n" errors)))
      (let [src-dir (first arguments)
            out-dir (second arguments)]
        (markdown/org-dir->md-dir src-dir out-dir options)))))

(def commands {"org-to-markdown" org-to-markdown
               "counts" counts/print-report
               "agenda" agenda/print-agenda})

(defn print-expectations []
  (println "Expected one of:" (keys commands)))

(defn -main [& args]
  (if-not (seq args)
    (do
      (println "No args passed")
      (print-expectations))
    (let [cmd  (first args)
          cmd  (commands cmd)
          args (rest args)]
      (if-not cmd
        (do
          (println "No command found for args: " args)
          (print-expectations))
        (apply cmd args)))))
