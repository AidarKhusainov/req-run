package com.github.aidarkhusainov.reqrun.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class ReqRunParserDefinition : ParserDefinition {
    override fun createLexer(project: com.intellij.openapi.project.Project?): Lexer = ReqRunLexer()

    override fun createParser(project: com.intellij.openapi.project.Project?): PsiParser =
        PsiParser { root, builder ->
            val marker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            marker.done(root)
            builder.treeBuilt
        }

    override fun getFileNodeType(): IFileElementType = ReqRunTypes.FILE

    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)

    override fun getCommentTokens(): TokenSet = TokenSet.EMPTY

    override fun getStringLiteralElements(): TokenSet = TokenSet.EMPTY

    override fun spaceExistenceTypeBetweenTokens(left: ASTNode?, right: ASTNode?): ParserDefinition.SpaceRequirements =
        ParserDefinition.SpaceRequirements.MAY

    override fun createElement(node: ASTNode): PsiElement = com.intellij.extapi.psi.ASTWrapperPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = ReqRunFile(viewProvider)
}
