package io.github.usernamec446.winterfront.util;

import java.util.Optional;
import java.util.stream.Stream;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;

/**
 * Optional config fields that report invalid values. {@code Codec#optionalFieldOf} silently uses
 * the default when a value is present but invalid, which would hide mistakes in config files.
 */
public final class StrictCodecs
{
    public static <A> MapCodec<A> optional(Codec<A> codec, String name, A defaultValue)
    {
        return new MapCodec<>()
        {
            @Override
            public <T> Stream<T> keys(DynamicOps<T> ops)
            {
                return Stream.of(ops.createString(name));
            }

            @Override
            public <T> DataResult<A> decode(DynamicOps<T> ops, MapLike<T> input)
            {
                final T value = input.get(name);
                if (value == null)
                {
                    return DataResult.success(defaultValue);
                }
                return codec.parse(ops, value).mapError(error -> name + ": " + error);
            }

            @Override
            public <T> RecordBuilder<T> encode(A input, DynamicOps<T> ops, RecordBuilder<T> prefix)
            {
                return prefix.add(name, codec.encodeStart(ops, input));
            }
        };
    }

    public static <A> MapCodec<Optional<A>> optional(Codec<A> codec, String name)
    {
        return optional(codec.xmap(Optional::of, Optional::get), name, Optional.empty());
    }

    private StrictCodecs() {}
}
