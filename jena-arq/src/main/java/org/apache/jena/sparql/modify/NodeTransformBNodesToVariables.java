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

package org.apache.jena.sparql.modify;

import java.util.HashMap ;
import java.util.Map ;
import org.apache.jena.graph.Node ;
import org.apache.jena.sparql.ARQConstants ;
import org.apache.jena.sparql.core.Var ;
import org.apache.jena.sparql.core.VarAlloc ;
import org.apache.jena.sparql.graph.NodeTransform;

public class NodeTransformBNodesToVariables implements NodeTransform
{
    private VarAlloc varAlloc = new VarAlloc(ARQConstants.allocVarBNodeToVar) ;
    private Map<Node, Var> mapping ;

    public NodeTransformBNodesToVariables()
    {
        this.mapping = new HashMap<>();
    }

    @Override
    public Node apply(Node node)
    {
        if ( ! node.isBlank() )
            return node ;
        Node node2 = mapping.get(node) ;
        if ( node2 == null )
        {
            Var v = varAlloc.allocVar() ;
            mapping.put(node, v) ;
            node2 = v ;
        }
        return node2 ;
    }
}
