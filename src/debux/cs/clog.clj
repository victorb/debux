(ns debux.cs.clog
  (:require [clojure.set :as set]
            [clojure.zip :as z]
            [cljs.analyzer :as analyzer]
            [debux.common.macro-specs :as ms]
            [debux.common.skip :as sk]
            [debux.common.util :as ut]
            [debux.cs.util :as cs.ut]
            [debux.cs.macro-types :as mt] ))

;;; clog macro
(defmacro clog
  "Console LOG an outer-most form."
  [form {:keys [n msg condition style js once] :as opts}]
  `(let [n# ~(or n 100)
         condition# ~condition
         result# ~form
         result# (if (coll? result#)
                   (ut/take-n result# n#)
                   result#)]
     (when (or (nil? condition#) condition#)
       (when (or (and ~once (cs.ut/changed? (str '~form " " '~opts) (str result#)))
                 (not ~once))
         (let [title# (str "%cclog: %c " (pr-str '~form)
                           " %c" (and ~msg (str "   <" ~msg ">"))
                           " =>" (and ~once "   (:once mode)"))
               style# (or ~style :debug)]
           (cs.ut/cgroup title# style#)
           (cs.ut/clog-result-with-indent result# @ut/indent-level* ~js)
           (cs.ut/cgroup-end) )))
     result#))

;;;; dbgn macro

;;; insert skip
(defn- insert-skip
   "Marks the form to skip."
  [form env]
  (loop [loc (ut/sequential-zip form)]
    (let [node (z/node loc)]
      ;(dbg node)
      (cond
        (z/end? loc) (z/root loc)

        ;; in case of (skip ...)
        (and (seq? node) (= `ms/skip (first node)))
        (recur (ut/right-or-next loc))

        (and (seq? node) (symbol? (first node)))
        (let [sym (ut/ns-symbol (first node) env)]
          (cond
            ((:def-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-def node))
                z/next
                recur)

            ((:defn-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-defn node))
                z/next
                recur)

            ((:fn-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-fn node))
                z/next
                recur)
            

            ((:let-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-let node))
                z/next
                recur)

            ((:letfn-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-letfn node))
                z/next
                recur)
            
                        
            ((:for-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-for node))
                z/next
                recur)

            ((:case-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-case node))
                z/next
                recur)
            

            ((:skip-arg-1-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-arg-1 node))
                z/next
                recur)

            ((:skip-arg-2-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-arg-2 node))
                z/next
                recur)
            
            ((:skip-arg-1-2-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-arg-1-2 node))
                z/next
                recur)
            
            ((:skip-arg-2-3-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-arg-2-3 node))
                z/next
                recur)
            
            ((:skip-arg-1-3-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-arg-1-3 node))
                z/next
                recur)
            
            ((:skip-form-itself-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-form-itself node))
                ut/right-or-next
                recur)
            

            ((:expand-type @mt/macro-types*) sym)
            (-> (z/replace loc (seq (analyzer/macroexpand-1 {} node)))
                recur)

            ((:dot-type @mt/macro-types*) sym)
            (-> (z/replace loc (sk/insert-skip-in-dot node))
                z/down
                z/right
                recur)

            :else
            (recur (z/next loc)) ))

        :else (recur (z/next loc)) ))))


;;; insert/remove d 
(defn- insert-d [form env]
  (loop [loc (ut/sequential-zip form)]
    (let [node (z/node loc)]
      ;(dbg node)
      (cond
        (z/end? loc) (z/root loc)

        ;; in case of (skip ...)
        (and (seq? node) (= `ms/skip (first node)))
        (recur (ut/right-or-next loc))

        ;; in case of (o-skip ...)
        (and (seq? node)
             (= `ms/o-skip (first node)))
        (cond
          ;; <ex> (o-skip [(skip a) ...]) 
          (vector? (second node))
          (recur (-> loc z/down z/next z/down))

          ;; <ex> (o-skip (recur ...))
          :else 
          (recur (-> loc z/down z/next z/down ut/right-or-next)))

        ;; in case that the first symbol is defn/defn-
        (and (seq? node)
             (symbol? (first node))
             (`#{defn defn-} (ut/ns-symbol(first node) env)))
        (recur (-> (-> loc z/down z/next)))

        ;; in case of the first symbol except defn/defn-/def
        (and (seq? node) (ifn? (first node)))
        (recur (-> (z/replace loc (concat [`d] [node]))
                   z/down z/right z/down ut/right-or-next))

        (vector? node)
        (recur (-> (z/replace loc (concat [`d] [node]))
                   z/down z/right z/down))
           
        ;; in case of symbol, map, or set
        (or (symbol? node) (map? node) (set? node))
        (recur (-> (z/replace loc (concat [`d] [node]))
                   ut/right-or-next))

        :else
        (recur (z/next loc) )))))

(defn- remove-d [form]
  (loop [loc (ut/sequential-zip form)]
    (let [node (z/node loc)]
      ;(ut/d node)
      (cond
        (z/end? loc) (z/root loc)

        ;; in case of (d ...)
        (and (seq? node)
             (= `d (first node)))
        (recur (z/replace loc (second node)))
      
        :else
        (recur (z/next loc)) ))))

   
(defmacro d [form]
  `(let [opts# ~'+debux-dbg-opts+
         msg#  (:msg opts#)
         n#    (or (:n opts#) 100)
         form-style# (or (:style opts#) :debug)
         result# ~form
         result# (if (coll? result#)
                   (ut/take-n result# n#)
                   result#)]
     (cs.ut/clog-form-with-indent (cs.ut/form-header '~(remove-d form) msg#)
                                  form-style# @ut/indent-level*)
     (cs.ut/clog-result-with-indent result# @ut/indent-level*)
     result#))


;;; remove skip
(defn- remove-skip [form]
  (loop [loc (ut/sequential-zip form)]
    (let [node (z/node loc)]
      ;(dbg node)
      (cond
        (z/end? loc) (z/root loc)

        ;; in case of (skip ...)
        (and (seq? node)
             (= `ms/skip (first node)))
        (recur (-> (z/replace loc (second node))
                   ut/right-or-next))

        ;; in case of (o-skip ...)
        (and (seq? node)
             (= `ms/o-skip (first node)))
        (recur (-> (z/replace loc (second node))
                   z/next))        
        

        :else
        (recur (z/next loc)) ))))


;;; clogn
(defmacro clogn
  "Console LOG every Nested forms of a form."
  [form & [{:keys [condition msg style] :as opts}]]
  `(let [~'+debux-dbg-opts+ ~(dissoc opts :js :once)
         condition#         ~condition]
     (try
       (when (or (nil? condition#) condition#)
         (let [title# (str "%cclogn: %c " (pr-str '~form)
                           " %c" (and ~msg (str "   <" ~msg ">"))
                           " =>")
               style# (or ~style :debug)]
           (ut/prog2
             (cs.ut/cgroup title# style#) 
             ~(-> form
                  (insert-skip &env)
                  (insert-d  &env)
                  remove-skip)
             (cs.ut/cgroup-end) )))
       (catch js/Error ~'e (throw ~'e)) )))


;;; break
(defmacro break
  "Sets a break point."
  [{:keys [msg condition] :as opts}]
  `(when (or (nil? ~condition) ~condition)
     (.log js/console (str "%c break %c"
                           (and ~msg (str "   <" ~msg ">")))
           "background: #FF1493; color: white"
           "background: white; color: black")   
     ~'(js* "debugger;") ))
