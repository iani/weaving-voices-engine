/*
* Premise

Say we want to listen to an event issued by an object, and perform an action when that event is issued.

* Elaborations
:END:

We can either:

1. always react uniformly to the event issued, in which case we can add different types of reactions.

*/

\object1 addDependant: { "received update message".postln; };

\object1.changed;


/*
2. devise ways to further enrich the protocol of the event issued by naming the event with a symbol.
*/

\object1 addDependant: (a = SimpleController(\object1));

a.put(\action1, { "this is action1".postln; });
a.put(\action2, { "this is action 2".postln; });

\object1.changed;
\object1.changed(\action1);
\object1.changed(\action2);

/*

3. Also further enrich the protocol so that each object listening to the event reacts differently according to the symbol further specifying the event.

There are several variants for case 2, which use symbols, and also add further refinements.  This will be explained below.

Explanation of above:

* Level 1: One reaction per event.
*/