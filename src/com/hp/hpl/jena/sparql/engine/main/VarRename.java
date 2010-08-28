/*
 * (c) Copyright 2010 Talis Systems Ltd.
 * All rights reserved.
 * [See end of file]
 */

package com.hp.hpl.jena.sparql.engine.main;

import java.util.ArrayList ;
import java.util.Collection ;
import java.util.List ;
import java.util.Set ;

import com.hp.hpl.jena.graph.Node ;
import com.hp.hpl.jena.graph.Triple ;
import com.hp.hpl.jena.sparql.ARQNotImplemented ;
import com.hp.hpl.jena.sparql.algebra.Op ;
import com.hp.hpl.jena.sparql.algebra.Transform ;
import com.hp.hpl.jena.sparql.algebra.TransformCopy ;
import com.hp.hpl.jena.sparql.algebra.Transformer ;
import com.hp.hpl.jena.sparql.algebra.op.* ;
import com.hp.hpl.jena.sparql.core.BasicPattern ;
import com.hp.hpl.jena.sparql.core.Var ;
import com.hp.hpl.jena.sparql.core.VarExprList ;
import com.hp.hpl.jena.sparql.engine.Renamer ;
import com.hp.hpl.jena.sparql.engine.RenamerLib ;
import com.hp.hpl.jena.sparql.engine.RenamerVars ;
import com.hp.hpl.jena.sparql.expr.ExprAggregator ;
import com.hp.hpl.jena.sparql.expr.Expr ;
import com.hp.hpl.jena.sparql.expr.ExprList ;


public class VarRename
{
    // See also OpVar, VarFiner and VarLib - needs to be pulled together really.
    // Also need to renaming support for renames where only a
    // certain set are mapped (for (assign (?x ?.0)))
    
    /** Rename all variables in a pattern, EXCEPT for those named as constant */ 
    public static Op rename(Op op, Collection<Var> constants)
    {
        return rename(op, new RenamerVars(constants)) ;
    }

    /** Rename all variables in a pattern, EXCEPT for those named as constant */ 
    public static Op rename(Op op, Renamer renamer)
    {
        Transform transform = new TransformRename(renamer) ; 
        return Transformer.transform(transform, op) ;
    }
    
    /** Rename all variables in an expression, EXCEPT for those named as constant */ 
    public static ExprList rename(ExprList exprList, Set<Var> constants)
    {
        Renamer renamer = new RenamerVars(constants) ;
        ExprList exprList2 = new ExprList() ;
        boolean changed = false ;
        for(Expr expr : exprList)
        {
            Expr expr2 = expr.copyNodeTransform(renamer) ;
            if ( expr != expr2 )
                changed = true ;
            exprList2.add(expr2) ;
        }
        if ( ! changed ) return exprList ;
        return exprList2 ;
    }
    
    /** Rename all variables in an expression, EXCEPT for those named as constant */ 
    public static Expr rename(Expr expr, Set<Var> constants)
    {
        Renamer renamer = new RenamerVars(constants) ;
        return expr.copyNodeTransform(renamer) ;
    }

    
    static class TransformRename extends TransformCopy
    {
        private final Renamer renamer ;
        public TransformRename(Renamer renamer)
        {
            this.renamer = renamer ;
        }

        @Override public Op transform(OpTriple opTriple)
        {
            Triple t2 = RenamerLib.rename(renamer, opTriple.getTriple()) ;
            if ( t2 == opTriple.getTriple())
                return super.transform(opTriple) ;
            return new OpTriple(t2) ;
        }
        
//        @Override public Op transform(OpQuad opQuad)
//        {
//            Quad q2 = rename(opQuad.getQuad()) ;
//            if ( q2 == opQuad.getQuad())
//                return opQuad ;
//            return new OpQuad(q2) ;
//        }
        
        
        @Override public Op transform(OpFilter opFilter, Op subOp)
        { 
            ExprList exprList = opFilter.getExprs() ;
            ExprList exprList2 = RenamerLib.rename(renamer, exprList) ;
            return OpFilter.filter(exprList2, subOp) ;
        }        
        
        @Override public Op transform(OpBGP opBGP)
        { 
            BasicPattern bgp2 = RenamerLib.rename(renamer, opBGP.getPattern()) ;
            if ( bgp2 == opBGP.getPattern())
                return super.transform(opBGP) ;
            return new OpBGP(bgp2) ;
        }
        
        @Override public Op transform(OpPath opPath) { return null ; }
        @Override public Op transform(OpQuadPattern opQuadPattern)
        { 
            // The internal representation is (graph, BGP)
            BasicPattern bgp2 = RenamerLib.rename(renamer, opQuadPattern.getBasicPattern()) ;
            Node g2 = opQuadPattern.getGraphNode() ;
            if ( g2 == opQuadPattern.getGraphNode() && bgp2 == opQuadPattern.getBasicPattern() )
                return super.transform(opQuadPattern) ;
            return new OpQuadPattern(g2, bgp2) ;
        }
        
        @Override public Op transform(OpGraph opGraph, Op subOp)
        {
            Node g2 = renamer.rename(opGraph.getNode()) ;
            if ( g2 == opGraph.getNode() )
                return super.transform(opGraph, subOp) ;
            return new OpGraph(g2, subOp) ;
        }
        
        @Override public Op transform(OpDatasetNames opDatasetNames)
        {
            Node g2 = renamer.rename(opDatasetNames.getGraphNode()) ;
            if ( g2 == opDatasetNames.getGraphNode() )
                return super.transform(opDatasetNames) ;
            return new OpDatasetNames(g2) ;
        }
        
        @Override public Op transform(OpTable opTable)
        {
            if ( opTable.isJoinIdentity() )
                return opTable ;
            
            throw new ARQNotImplemented() ;
            //return null ;
        }
        
        @Override public Op transform(OpProject opProject, Op subOp)
        { 
            List<Var> x = opProject.getVars() ;
            List<Var> x2 = RenamerLib.renameVars(renamer, x) ;
            return new OpProject(subOp, x2) ; 
        }
        
        @Override public Op transform(OpAssign opAssign, Op subOp)
        { 
            VarExprList varExprList = opAssign.getVarExprList() ;
            VarExprList varExprList2 = RenamerLib.rename(renamer, varExprList) ;
            return OpAssign.assign(subOp, varExprList2) ;
        }
        
        @Override public Op transform(OpGroup opGroup, Op subOp)
        {
            VarExprList groupVars = RenamerLib.rename(renamer, opGroup.getGroupVars()) ;

            // Rename the vars in the expression as well.
            // .e.g max(?y) ==> max(?/y)  
            // These need renaming as well.
            List<ExprAggregator> aggregators = new ArrayList<ExprAggregator>() ;
            for ( ExprAggregator agg : opGroup.getAggregators() )
                aggregators.add(agg.copyNodeTransform(renamer)) ;

            //if ( true )throw new ARQNotImplemented() ;
            return new OpGroup(subOp, groupVars, aggregators) ;
        }
    }
    

}

/*
 * (c) Copyright 2010 Talis Systems Ltd.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */