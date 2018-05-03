package org.zstack.zql.ast.visitors;

import org.apache.commons.lang.StringUtils;
import org.zstack.header.zql.ASTNode;
import org.zstack.header.zql.ASTVisitor;

import java.util.ArrayList;
import java.util.List;

public class RestrictByVisitor implements ASTVisitor<String, ASTNode.RestrictBy> {
    @Override
    public String visit(ASTNode.RestrictBy node) {
        List<String> conds = new ArrayList<>();
        node.getExprs().forEach(it -> {
            conds.add((String) it.accept(new RestrictByVisitor()));
        });

        return conds.isEmpty() ? null : String.format("(%s)", StringUtils.join(conds, " AND "));
    }
}
