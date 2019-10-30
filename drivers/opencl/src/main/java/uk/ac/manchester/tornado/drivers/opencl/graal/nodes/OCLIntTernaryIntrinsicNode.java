///*
// * Copyright (c) 2018, 2019, APT Group, School of Computer Science,
// * The University of Manchester. All rights reserved.
// * Copyright (c) 2009, 2017, Oracle and/or its affiliates. All rights reserved.
// * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
// *
// * This code is free software; you can redistribute it and/or modify it
// * under the terms of the GNU General Public License version 2 only, as
// * published by the Free Software Foundation.
// *
// * This code is distributed in the hope that it will be useful, but WITHOUT
// * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
// * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
// * version 2 for more details (a copy is included in the LICENSE file that
// * accompanied this code).
// *
// * You should have received a copy of the GNU General Public License version
// * 2 along with this work; if not, write to the Free Software Foundation,
// * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
// *
// * Authors: James Clarkson
// *
// */
//package uk.ac.manchester.tornado.drivers.opencl.graal.nodes;
//
//import static uk.ac.manchester.tornado.api.exceptions.TornadoInternalError.shouldNotReachHere;
//
//import org.graalvm.compiler.core.common.LIRKind;
//import org.graalvm.compiler.core.common.type.Stamp;
//import org.graalvm.compiler.core.common.type.StampFactory;
//import org.graalvm.compiler.graph.Node;
//import org.graalvm.compiler.graph.NodeClass;
//import org.graalvm.compiler.graph.spi.CanonicalizerTool;
//import org.graalvm.compiler.lir.Variable;
//import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
//import org.graalvm.compiler.nodeinfo.NodeInfo;
//import org.graalvm.compiler.nodes.ConstantNode;
//import org.graalvm.compiler.nodes.NodeView;
//import org.graalvm.compiler.nodes.ValueNode;
//import org.graalvm.compiler.nodes.calc.BinaryNode;
//import org.graalvm.compiler.nodes.calc.TernaryNode;
//import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
//import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;
//
//import jdk.vm.ci.meta.JavaKind;
//import jdk.vm.ci.meta.Value;
//import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
//import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
//import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLArithmeticTool;
//import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLBuiltinTool;
//import uk.ac.manchester.tornado.drivers.opencl.graal.lir.OCLLIRStmt.AssignStmt;
//
//
////TODO seems like Ternary Node is not available in the Java 11 compiler.
//// For now we copy bits of the old implementation to continue supporting ternary nodes.
//// NOTE: we only use ternary nodes for the CLAMP function
//@NodeInfo(nameTemplate = "{p#operation/s}")
//public class OCLIntTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable {
//
//    protected OCLIntTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
//        super(TYPE, StampFactory.forKind(kind), x, y, z);
//        this.operation = op;
//    }
//
//    public static final NodeClass<OCLIntTernaryIntrinsicNode> TYPE = NodeClass.create(OCLIntTernaryIntrinsicNode.class);
//    protected final Operation operation;
//
//    @Override
//    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
//        return stamp(NodeView.DEFAULT);
//    }
//
//    public enum Operation {
//        CLAMP, MAD_HI, MAD_SAT, MAD24
//    }
//
//    public Operation operation() {
//        return operation;
//    }
//
//    public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
//        ValueNode c = tryConstantFold(x, y, z, op, kind);
//        if (c != null) {
//            return c;
//        }
//        return new OCLIntTernaryIntrinsicNode(x, y, z, op, kind);
//    }
//
//    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
//        ConstantNode result = null;
//
//        if (x.isConstant() && y.isConstant() && z.isConstant()) {
//            if (kind == JavaKind.Int) {
//                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), z.asJavaConstant().asInt(), op);
//                result = ConstantNode.forInt(ret);
//            } else if (kind == JavaKind.Long) {
//                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), z.asJavaConstant().asInt(), op);
//                result = ConstantNode.forLong(ret);
//            }
//        }
//        return result;
//    }
//
//    @Override
//    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGen) {
//        OCLBuiltinTool gen = ((OCLArithmeticTool) lirGen).getGen().getOCLBuiltinTool();
//
//        Value x = getX().isConstant() ? builder.operand(getX()) : builder.operand(getX());
//        Value y = getY().isConstant() ? builder.operand(getY()) : builder.operand(getY());
//        Value z = getZ().isConstant() ? builder.operand(getZ()) : builder.operand(getZ());
//        LIRKind lirKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
//        Variable result = builder.getLIRGeneratorTool().newVariable(lirKind);
//        Value expr;
//        switch (operation()) {
//            case CLAMP:
//                expr = gen.genIntClamp(x, y, z);
//                break;
//            case MAD24:
//                expr = gen.genIntMad24(x, y, z);
//                break;
//            case MAD_HI:
//                expr = gen.genIntMadHi(x, y, z);
//                break;
//            case MAD_SAT:
//                expr = gen.genIntMadSat(x, y, z);
//                break;
//            default:
//                throw shouldNotReachHere();
//        }
//        builder.getLIRGeneratorTool().append(new AssignStmt(result, expr));
//        builder.setResult(this, result);
//
//    }
//
//    private static long doCompute(long x, long y, long z, Operation op) {
//        switch (op) {
//            default:
//                throw new TornadoInternalError("unknown op %s", op);
//        }
//    }
//
//    private static int doCompute(int x, int y, int z, Operation op) {
//        switch (op) {
//            case CLAMP:
//                return TornadoMath.clamp(x, y, z);
//
//            default:
//                throw new TornadoInternalError("unknown op %s", op);
//        }
//    }
//
//    @Override
//    public Node canonical(CanonicalizerTool tool, ValueNode x, ValueNode y, ValueNode z) {
//        ValueNode c = tryConstantFold(x, y, z, operation(), getStackKind());
//        if (c != null) {
//            return c;
//        }
//        return this;
//    }
//
//}
