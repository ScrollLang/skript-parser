package io.github.syst3ms.skriptparser.lang;

import io.github.syst3ms.skriptparser.classes.ChangeMode;
import io.github.syst3ms.skriptparser.event.Event;
import io.github.syst3ms.skriptparser.parsing.SkriptParserException;
import io.github.syst3ms.skriptparser.parsing.SkriptRuntimeException;
import io.github.syst3ms.skriptparser.registration.ExpressionInfo;
import io.github.syst3ms.skriptparser.registration.SyntaxManager;
import io.github.syst3ms.skriptparser.types.ConvertedExpression;
import io.github.syst3ms.skriptparser.util.CollectionUtils;

import java.util.Iterator;

public interface Expression<T> extends SyntaxElement {
    T[] getValues(Event e);

    default T[] getArray(Event e) {
        return getValues(e);
    }

    default boolean change(Event e, ChangeMode changeMode) {
        return false;
    }

    default T getSingle(Event e) {
        T[] values = getValues(e);
        if (values.length == 0) {
            return null;
        } else if (values.length > 1) {
            throw new SkriptRuntimeException("Can't call getSingle on an expression that returns multiple values !");
        } else {
            return values[0];
        }
    }

    default boolean isSingle() {
        for (ExpressionInfo<?, ?> info : SyntaxManager.getAllExpressions()) {
            if (info.getSyntaxClass() == getClass()) {
                return info.getReturnType().isSingle();
            }
        }
        throw new SkriptParserException("Unregistered expression class : " + getClass().getName());
    }

    default Class<? extends T> getReturnType() {
        ExpressionInfo<?, T> info = SyntaxManager.getExpressionExact(this);
        if (info == null) {
            throw new SkriptParserException("Unregistered expression class : " + getClass().getName());
        }
        return info.getReturnType().getType().getTypeClass();
    }

    default Iterator<? extends T> iterator(Event e) {
        return CollectionUtils.iterator(getValues(e));
    }

    default <C> Expression<C> convertExpression(Class<C>... to) {
        return ConvertedExpression.newInstance(this, to);
    }

    default boolean isLoopOf(String s) {
        return s.equals("value");
    }

    default boolean isAndList() {
        return true;
    }

    default void setAndList(boolean isAndList) {
    }

    default Expression<?> getSource() {
        return this;
    }
}
