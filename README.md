# stately

![](doc/state_house.jpg)


## Status

*pre* pre alpha quality. More of an idea than anything useful at this point. Use
at your own risk

This readme is a work in progress as well.  I will update as time allows

### next steps
* testing -- in progress
* better examples -- on deck

## Rational

Over the course of my career I have come across numerous scenarios that involve
managing a lifecycle of side effects that are related to domain entities.
The most frequent manifestation of this scenario that I have seen is writing cron
jobs, or polling a database to look for domain entity `X` and do `Y` with it, e.g.
notify a user or update some attribute. These are your "batch jobs" or
"workflows".

One of the problems I have consistently run into in this scenario is
that I wind up with a muddled bunch of spaghetti code that is trying
to manage the lifecycle of `X` doing `Y` *and* handle the side effects
simultaneously.  I have had this blow up on me a sufficient number of times
to write this library in anger.

Stately is a collection of protocols that allows you to build up a
Finite State Machine (FSM) to manage the state of these side effect lifecycles in
terms of the domain entity itself and allows you to advance through those stages
without the need for batch jobs. It also offers a separation of concerns of
managing that lifecycle and handling the side effects of that life cycle by handing
control of handling the side effects of these lifecycle state transitions over to
a separate mechanism outside of state management.

## Usage


### Necessary Core Components
Stately provides the building blocks, the usage is entirely up to you.
Stately needs a few things to be useful.
* something that implements `stately.components.state-store/StateStore`
* something that implements `stately.components.data-store/DataStore`
* something that implements `stately.components.executor/Executor`
* a handle state function.  I choose to implement this as a 4-arity
  multi-method dispatched on state in my `SimpleStately` impl.
* a data function.  A function, given the core and a reference, knows
  how to find the data needed to complete the task at hand

These three building blocks will make up a `stately.core/IStatelyCore`,
The `IStatelyCore` will be our bridge from our FSM to communicating with
the rest of the system.

I have provided some basic examples of these implementations, but these could be
extended to anything that fulfills those protocols.  For the stores, I imagine I
will be throwing something together to hook this up to Datomic fairly soon.

### Nodes

Nodes represent individual states in the lifecycle of an entity.
a Stately state machine is, at its core, a Directed Graph, and the nodes
are the vertices. There is documentation [here](src/stately/graph/nodes.clj)
in the code.  I have also helper fns for creating what I consider to be the
most useful type of nodes.

### StateMachine

The StateMachine derives a digraph from a map of node names to node definitions.
the machine uses the graph to enforce the invariant of a state machine.

a basic example of creating one would be:

```clojure
(def nodes {:start start-node-default
            :foo   foo-node-def
            :bar   bar-node-def})

(stately.machine.state-machine/make-machine nodes)
```


### Stately

Where everything comes together. Finally. The `state.core.defstately` macro will create a named and bound fn that will in turn create a Stately impl that you can operate on, given a component that implements  `StatelyComponent`.

The macro takes 4 args
* name - a name to bind to
* state-machine - the StateMachine that manages the lifecycle
* data-fn - given an impl of `stately.components.data-store/DataStore`,
 how to get the data for a reference
* state-handler-fn - a multimehod on the state.  This is the function that
handles the side effects that result from state transitions. The machine
handles the lifecycle, this handles the side effects. This is the separation of
concerns.

at a high level, this works as follows

```clojure
(defn simple-data-fn [data-store ref]
  (get  @data-store ref))

(defmulti handle-state-fn
  (fn [_ new-state _ _] new-state))

;;... define impl of handlers here

(def nodes {:start start-node-default
            :foo   foo-node-def
            :bar   bar-node-def})

(def machine (stately.machine.state-machine/make-machine nodes))

(stately.core/defstately my-stately machine simple-data-fn handle-state-fn)

;; now you have something you can send events to

(-> (my-stately my-stately-component my-data-reference)
    (input my-event))
```

### Complete Examples
I have also provided a very basic example in the `dev/user` namespace, based
on a system created in `dev/mock-system`.

to see it in action, repl in and

```clojure
(go)
(run-everyone)
```

you will have ~30 seconds to poke around using the functions in the user NS to see
what is going on in the state store, data store, etc...




## Acknowledgments

### Standing on the shoulders of giants

* this library is the product of a lot of work with and many discussions with
both Ian Eslick and Adrian Medina. While born out of trying to solve a very
specific problem in my work with them, I saw the possibility to solve a more
general problem.  Thanks guys.

* Thanks to [aysylu](https://github.com/aysylu) for providing [loom](https://github.com/aysylu/loom), which set me down the path of thinking about digraphs as abstractions for state machines, and provides the impls for them in stately.


## License

Copyright © 2015

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
