package com.kalessil.phpStorm.phpInspectionsEA.inspectors.languageConstructions;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.jetbrains.php.config.PhpLanguageFeature;
import com.jetbrains.php.config.PhpLanguageLevel;
import com.jetbrains.php.config.PhpProjectConfigurationFacade;
import com.jetbrains.php.lang.PhpLangUtil;
import com.jetbrains.php.lang.psi.elements.*;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpElementVisitor;
import com.kalessil.phpStorm.phpInspectionsEA.openApi.BasePhpInspection;
import com.kalessil.phpStorm.phpInspectionsEA.utils.ExpressionSemanticUtil;
import org.jetbrains.annotations.NotNull;

public class NullCoalescingOperatorCanBeUsedInspector extends BasePhpInspection {
    private static final String strProblemDescription = "' ... ?? ...' construction shall be used instead";
    private static final String strProblemCandidate   = "isset(...) can be used instead (was wrongly reported in older inspections set)";

    @NotNull
    public String getShortName() {
        return "NullCoalescingOperatorCanBeUsedInspection";
    }

    @Override
    public PsiElementVisitor buildVisitor(@NotNull final ProblemsHolder holder, boolean isOnTheFly) {
        return new BasePhpElementVisitor() {
            public void visitPhpTernaryExpression(TernaryExpression expression) {
                PhpLanguageLevel preferableLanguageLevel = PhpProjectConfigurationFacade.getInstance(holder.getProject()).getLanguageLevel();
                if (!preferableLanguageLevel.hasFeature(PhpLanguageFeature.COALESCE_OPERATOR)) {
                    return;
                }

                PsiElement issetCandidate = ExpressionSemanticUtil.getExpressionTroughParenthesis(expression.getCondition());
                if (issetCandidate instanceof PhpIsset) {
                    PhpIsset isset = (PhpIsset) issetCandidate;
                    if (isset.getVariables().length != 1) {
                        return;
                    }

                    /* construction requirements */
                    final PsiElement objTrueVariant = ExpressionSemanticUtil.getExpressionTroughParenthesis(expression.getTrueVariant());
                    if (null == objTrueVariant) {
                        return;
                    }
                    final PsiElement objCondition = ExpressionSemanticUtil.getExpressionTroughParenthesis(isset.getVariables()[0]);
                    if (null == objCondition) {
                        return;
                    }

                    if (PsiEquivalenceUtil.areElementsEquivalent(objCondition, objTrueVariant)) {
                        holder.registerProblem(objTrueVariant, strProblemDescription, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
                    }

                }

                /* older version might influence isset->array_key_exists in ternary conditions */
                if (issetCandidate instanceof FunctionReference && !(issetCandidate instanceof MethodReference)) {
                    String functionName = ((FunctionReference) issetCandidate).getName();
                    if (!StringUtil.isEmpty(functionName) && functionName.equals("array_key_exists")) {
                        /* when array_key_exists alternative value is not null, it intended to be so */
                        final PsiElement objFalseVariant = ExpressionSemanticUtil.getExpressionTroughParenthesis(expression.getFalseVariant());
                        if (!(objFalseVariant instanceof ConstantReference) || !PhpLangUtil.isNull((ConstantReference) objFalseVariant)) {
                            return;
                        }

                        holder.registerProblem(issetCandidate, strProblemCandidate, ProblemHighlightType.WEAK_WARNING);
                    }
                }
            }
        };
    }
}