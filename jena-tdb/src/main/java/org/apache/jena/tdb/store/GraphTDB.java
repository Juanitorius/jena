/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.tdb.store ;

import java.util.Iterator ;
import java.util.function.Function;

import org.apache.jena.atlas.iterator.Iter ;
import org.apache.jena.atlas.lib.Closeable ;
import org.apache.jena.atlas.lib.Sync ;
import org.apache.jena.atlas.lib.tuple.Tuple ;
import org.apache.jena.atlas.lib.tuple.TupleFactory ;
import org.apache.jena.graph.Capabilities ;
import org.apache.jena.graph.GraphEvents ;
import org.apache.jena.graph.Node ;
import org.apache.jena.graph.Triple ;
import org.apache.jena.graph.impl.AllCapabilities ;
import org.apache.jena.riot.other.GLib ;
import org.apache.jena.shared.PrefixMapping ;
import org.apache.jena.sparql.core.DatasetGraph ;
import org.apache.jena.sparql.core.DatasetPrefixStorage ;
import org.apache.jena.sparql.core.GraphView ;
import org.apache.jena.sparql.core.Quad ;
import org.apache.jena.tdb.TDBException ;
import org.apache.jena.tdb.store.nodetupletable.NodeTupleTable ;
import org.apache.jena.util.iterator.ExtendedIterator ;
import org.apache.jena.util.iterator.WrappedIterator ;

/**
 * General operations for TDB graphs
 * (free-standing graph, default graph and named graphs)
 */
public abstract class GraphTDB extends GraphView implements Closeable, Sync {
    public GraphTDB(DatasetGraph dataset, Node graphName) {
        super(dataset, graphName) ;
    }

    /** Return the associated DatasetGraphTDB.
     * For non-transactional, that's the base storage.
     * For transactional, it is the transactional view.
     * <p>
     * Immediate validity only.
     * Not valid actoss transacion boundaries, nor non-transactional to transactional. 
     */
    public abstract DatasetGraphTDB getDatasetGraphTDB() ;
    
    /** Return the associated base DatasetGraphTDB storage.
     * Use with great care.
     * <p>
     * Immediate validity only.
     */
    protected abstract DatasetGraphTDB getBaseDatasetGraphTDB() ;
    
    protected DatasetPrefixStorage getPrefixStorage() {
        return getDatasetGraphTDB().getPrefixes() ;
    }

    /** The NodeTupleTable for this graph - valid only inside the transaction or non-transactional. */
    public NodeTupleTable getNodeTupleTable() {
        return getDatasetGraphTDB().chooseNodeTupleTable(getGraphName()) ;
    }

    @Override
    protected PrefixMapping createPrefixMapping() {
        // [TXN] Make transactional.
        DatasetPrefixStorage dsgPrefixes = getDatasetGraphTDB().getPrefixes() ;
        if ( isDefaultGraph() )
            return dsgPrefixes.getPrefixMapping() ;
        if ( isUnionGraph() )
            return dsgPrefixes.getPrefixMapping() ;
        return dsgPrefixes.getPrefixMapping(getGraphName().getURI()) ;
    }

    @Override
    public final void sync() {
        getDatasetGraphTDB().sync();
    }

    @Override
    final public void close() {
        sync() ;
        // Don't close the dataset.
        super.close() ;
    }

    @Override
    protected ExtendedIterator<Triple> graphUnionFind(Node s, Node p, Node o) {
        Iterator<Quad> iterQuads = getDatasetGraphTDB().find(Quad.unionGraph, s, p, o) ;
        Iterator<Triple> iter = GLib.quads2triples(iterQuads) ;
        // Suppress duplicates after projecting to triples.
        // TDB guarantees that duplicates are adjacent.
        // See SolverLib.
        iter = Iter.distinctAdjacent(iter) ;
        return WrappedIterator.createNoRemove(iter) ;
    }

    @Override
    protected final int graphBaseSize() {
        if ( isDefaultGraph() )
            return (int)getNodeTupleTable().size() ;

        Node gn = getGraphName() ;
        boolean unionGraph = isUnionGraph(gn) ;
        gn = unionGraph ? Node.ANY : gn ;
        QuadTable quadTable = getDatasetGraphTDB().getQuadTable() ;
        Iterator<Tuple<NodeId>> iter = quadTable.getNodeTupleTable().findAsNodeIds(gn, null, null, null) ;
        if ( unionGraph ) {
            iter = Iter.map(iter, project4TupleTo3Tuple) ;
            iter = Iter.distinctAdjacent(iter) ;
        }
        return (int)Iter.count(iter) ;
    }

	private static Function<Tuple<NodeId>, Tuple<NodeId>> project4TupleTo3Tuple = item -> {
		if (item.len() != 4)
			throw new TDBException("Expected a Tuple of 4, got: " + item);
		return TupleFactory.tuple(item.get(1), item.get(2), item.get(3));
	};

    @Override
    public Capabilities getCapabilities() {
        if ( capabilities == null )
            capabilities = new AllCapabilities() {
                @Override public boolean iteratorRemoveAllowed() { return false ; } 
                @Override public boolean handlesLiteralTyping() { return false ; }
            } ;
        return super.getCapabilities() ;
    }
    
    @Override
    public void clear() {
        getDatasetGraphTDB().deleteAny(getGraphName(), Node.ANY, Node.ANY, Node.ANY) ;
        getEventManager().notifyEvent(this, GraphEvents.removeAll) ;
    }

    @Override
    public void remove(Node s, Node p, Node o) {
        if ( getEventManager().listening() ) {
            // Have to do it the hard way so that triple events happen.
            super.remove(s, p, o) ;
            return ;
        }

        getDatasetGraphTDB().deleteAny(getGraphName(), s, p, o) ;
        // We know no one is listening ...
        // getEventManager().notifyEvent(this, GraphEvents.remove(s, p, o) ) ;
    }
}
