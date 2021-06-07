package uk.ac.manchester.tornado.drivers.spirv.graal.nodes;

import org.graalvm.compiler.core.common.LIRKind;
import org.graalvm.compiler.core.common.type.Stamp;
import org.graalvm.compiler.core.common.type.StampFactory;
import org.graalvm.compiler.graph.Node;
import org.graalvm.compiler.graph.NodeClass;
import org.graalvm.compiler.graph.spi.CanonicalizerTool;
import org.graalvm.compiler.lir.Variable;
import org.graalvm.compiler.lir.gen.ArithmeticLIRGeneratorTool;
import org.graalvm.compiler.nodeinfo.NodeInfo;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.NodeView;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.calc.TernaryNode;
import org.graalvm.compiler.nodes.spi.ArithmeticLIRLowerable;
import org.graalvm.compiler.nodes.spi.NodeLIRBuilderTool;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.Value;
import uk.ac.manchester.tornado.api.collections.math.TornadoMath;
import uk.ac.manchester.tornado.api.exceptions.TornadoInternalError;
import uk.ac.manchester.tornado.drivers.spirv.graal.compiler.lir.SPIRVArithmeticTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVBuiltinTool;
import uk.ac.manchester.tornado.drivers.spirv.graal.lir.SPIRVLIRStmt;
import uk.ac.manchester.tornado.runtime.graal.phases.MarkIntIntrinsicNode;

@NodeInfo(nameTemplate = "{p#operation/s}")
public class SPIRVIntTernaryIntrinsicNode extends TernaryNode implements ArithmeticLIRLowerable, MarkIntIntrinsicNode {

    public static final NodeClass<SPIRVIntTernaryIntrinsicNode> TYPE = NodeClass.create(SPIRVIntTernaryIntrinsicNode.class);
    protected final Operation operation;

    public enum Operation {
        CLAMP, MAD_HI, MAD_SAT, MAD24
    }

    protected SPIRVIntTernaryIntrinsicNode(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        super(TYPE, StampFactory.forKind(kind), x, y, z);
        this.operation = op;
    }

    public Operation operation() {
        return operation;
    }

    @Override
    public String getOperation() {
        return operation.toString();
    }

    public static ValueNode create(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ValueNode c = tryConstantFold(x, y, z, op, kind);
        if (c != null) {
            return c;
        }
        return new SPIRVIntTernaryIntrinsicNode(x, y, z, op, kind);
    }

    private static long doCompute(long x, long y, long z, Operation op) {
        throw new TornadoInternalError("unknown op %s", op);
    }

    private static int doCompute(int x, int y, int z, Operation op) {
        if (op == Operation.CLAMP) {
            return TornadoMath.clamp(x, y, z);
        }
        throw new TornadoInternalError("unknown op %s", op);
    }

    protected static ValueNode tryConstantFold(ValueNode x, ValueNode y, ValueNode z, Operation op, JavaKind kind) {
        ConstantNode result = null;

        if (x.isConstant() && y.isConstant() && z.isConstant()) {
            if (kind == JavaKind.Int) {
                int ret = doCompute(x.asJavaConstant().asInt(), y.asJavaConstant().asInt(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forInt(ret);
            } else if (kind == JavaKind.Long) {
                long ret = doCompute(x.asJavaConstant().asLong(), y.asJavaConstant().asLong(), z.asJavaConstant().asInt(), op);
                result = ConstantNode.forLong(ret);
            }
        }
        return result;
    }

    @Override
    public Stamp foldStamp(Stamp stampX, Stamp stampY, Stamp stampZ) {
        return stamp(NodeView.DEFAULT);
    }

    @Override
    public Node canonical(CanonicalizerTool tool, ValueNode forX, ValueNode forY, ValueNode forZ) {
        ValueNode c = tryConstantFold(x, y, z, operation(), getStackKind());
        if (c != null) {
            return c;
        }
        return this;
    }

    @Override
    public void generate(NodeLIRBuilderTool builder, ArithmeticLIRGeneratorTool lirGeneratorTool) {

        SPIRVBuiltinTool gen = ((SPIRVArithmeticTool) lirGeneratorTool).getGen().getSpirvBuiltinTool();

        Value x = builder.operand(getX());
        Value y = builder.operand(getY());
        Value z = builder.operand(getZ());
        LIRKind lirKind = builder.getLIRGeneratorTool().getLIRKind(stamp);
        Variable result = builder.getLIRGeneratorTool().newVariable(lirKind);
        Value expr;
        switch (operation()) {
            case CLAMP:
                expr = gen.genIntClamp(x, y, z);
                break;
            default:
                throw new RuntimeException("Ternary Intrinsic not supported: " + operation);
        }

        builder.getLIRGeneratorTool().append(new SPIRVLIRStmt.AssignStmt(result, expr));
        builder.setResult(this, result);

    }

}
