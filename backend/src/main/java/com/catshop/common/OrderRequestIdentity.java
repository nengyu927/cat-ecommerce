package com.catshop.common;

import com.catshop.dto.order.CreateOrderRequest;
import com.catshop.exception.BusinessException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/** Hash the exact submitted body fields, with length framing and distinct NULL values. */
public final class OrderRequestIdentity {
    private OrderRequestIdentity() {}

    public static String normalize(String id) {
        if (id == null) return null; // Optional only for backward compatibility.
        if (!id.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw BusinessException.badRequest("下單識別碼必須為 UUID");
        }
        return id.toLowerCase(Locale.ROOT);
    }

    public static String hash(CreateOrderRequest req) {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            for (String value : new String[]{req.receiverName(), req.receiverPhone(), req.shippingAddress(),
                    req.remark(), req.paymentMethod(), req.couponCode()}) {
                byte[] bytes = value == null ? null : value.getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes == null ? -1 : bytes.length).array());
                if (bytes != null) digest.update(bytes);
            }
            // Do not append NULL: preserve hashes of requests/orders created before snapshot support.
            if (req.cartSnapshot() != null) {
                byte[] bytes = req.cartSnapshot().getBytes(StandardCharsets.UTF_8);
                digest.update(ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            if (req.expectedAmounts()!=null) {
                var a=req.expectedAmounts();
                for (String value : new String[]{a.goodsTotal()==null?null:a.goodsTotal().stripTrailingZeros().toPlainString(),
                        a.discountAmount()==null?null:a.discountAmount().stripTrailingZeros().toPlainString(),
                        a.shippingFee()==null?null:a.shippingFee().stripTrailingZeros().toPlainString(),
                        a.totalAmount()==null?null:a.totalAmount().stripTrailingZeros().toPlainString(),a.priceSnapshot()}) {
                    byte[] bytes=value==null?null:value.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(4).putInt(bytes==null?-1:bytes.length).array());
                    if(bytes!=null)digest.update(bytes);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
