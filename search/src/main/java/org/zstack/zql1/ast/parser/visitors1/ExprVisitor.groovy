package org.zstack.zql1.ast.parser.visitors1

import org.zstack.zql1.antlr4.ZQLBaseVisitor
import org.zstack.zql1.antlr4.ZQLParser
import org.zstack.header.zql.ASTNode

class ExprVisitor extends ZQLBaseVisitor<ASTNode.Expr> {
    @Override
    ASTNode.Expr visitExpr(ZQLParser.ExprContext ctx) {
        return new ASTNode.Expr(
                left: ctx.field().ID().collect {it.getText()},
                operator: ctx.operator().getText(),
                right: ctx.complexValue() == null ? null : ctx.complexValue().accept(new ValueVisitor())
        )
    }
}
