# multi-threaded-applicaiton

Search engines need to find quickly what files contain a word or a group of words (like a sentence). They often achieve this by constructing a so-called inverted index, which maps each words to all the files that contain it. Constructing an inverted index can be costly, but will speed up future searches. In this project, the objective is to implement an inverted index that can safely be shared by multiple threads. The design principles are as follows:

• We assume many more read operations (for searches) than modifications (to add or remove files) on the index. Accordingly, threads should be able to fully search the index in parallel, even while the index is being modified.

• The index is implemented as a persistent (immutable) data structure, which allows for parallel reads.Threads only need to obtain a reference on the structure (which is fast), after which they can read it without further synchronization.

• Modifications to the index are run in the background to build a new immutable structure. These do not interfere with searches. When a modification is complete, the new data structure is committed as a replacement for the current one, which is a fast operation.
