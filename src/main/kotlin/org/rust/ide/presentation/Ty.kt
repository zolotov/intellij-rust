/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.ide.presentation

import org.rust.lang.core.types.ty.*


val Ty.shortPresentableText: String get() = render(this, level = 3)

fun tyToString(ty: Ty) = render(ty, Int.MAX_VALUE)

private fun render(ty: Ty, level: Int): String {
    check(level >= 0)
    if (ty is TyUnknown) return "<unknown>"
    if (ty is TyPrimitive) {
        return when (ty) {
            is TyBool -> "bool"
            is TyChar -> "char"
            is TyUnit -> "()"
            is TyNever -> "!"
            is TyStr -> "str"
            is TyInteger -> ty.kind.toString()
            is TyFloat -> ty.kind.toString()
            else -> error("unreachable")
        }
    }

    if (level == 0) return "_"

    val r = { subTy: Ty -> render(subTy, level - 1) }
    val anonymous = "<anonymous>"

    return when (ty) {
        is TyFunction -> {
            val params = ty.paramTypes.joinToString(", ", "fn(", ")", transform = r)
            return if (ty.retType is TyUnit) params else "$params -> ${ty.retType}"

        }
        is TySlice -> "[${r(ty.elementType)}]"

        is TyTuple -> ty.types.joinToString(", ", "(", ")", transform = r)
        is TyArray -> "[${r(ty.base)}; ${ty.size ?: "<unknown>"}]"
        is TyReference -> "${if (ty.mutability.isMut) "&mut " else "&"}${render(ty.referenced, level)}"
        is TyPointer -> "*${if (ty.mutability.isMut) "mut" else "const"} ${r(ty.referenced)}"
        is TyTraitObject -> ty.trait.name ?: anonymous
        is TyTypeParameter -> ty.name ?: anonymous
        is TyStructOrEnumBase -> {
            val name = ty.item.name ?: return anonymous
            name + if (ty.typeArguments.isEmpty()) "" else ty.typeArguments.joinToString(", ", "<", ">", transform = r)
        }
        is TyInfer -> when (ty) {
            is TyInfer.TyVar -> "_"
            is TyInfer.IntVar -> "{integer}"
            is TyInfer.FloatVar -> "{float}"
        }
        else -> error("unreachable")
    }
}
