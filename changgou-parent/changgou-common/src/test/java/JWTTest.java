import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

public class JWTTest {
    /****
     * 创建Jwt令牌
     */
    @Test
    public void testCreateJwt(){
        Date now = new Date();
        JwtBuilder builder= Jwts.builder()
                .setId("888")             //设置唯一编号
                .setSubject("小白")       //设置主题  可以是JSON数据
                .setIssuedAt(now)  //设置签发日期
//                .setExpiration(new Date(now.getTime() + 60 * 5))  //设置过期时间
                .signWith(SignatureAlgorithm.HS256,"itcast");//设置签名 使用HS256算法，并设置SecretKey(字符串)

        //3.可以自定义载荷
        Map<String, Object> map = new HashMap<>();
        map.put("myaddress","cn");
        map.put("mycity","sz");
        builder.addClaims(map);


        //构建 并返回一个字符串
        System.out.println( builder.compact() );
    }


    /***
     * 解析Jwt令牌数据
     */
    @Test
    public void testParseJwt(){
        String compactJwt="eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiI4ODgiLCJzdWIiOiLlsI_nmb0iLCJpYXQiOjE1OTQ0NTIxOTAsIm15Y2l0eSI6InN6IiwibXlhZGRyZXNzIjoiY24ifQ.7qIlRcdwyBksD6ZRYyFdADfytgEsi3KtxPQ_XJEI1FM";
        Claims claims = Jwts.parser().
                setSigningKey("itcast").
                parseClaimsJws(compactJwt).
                getBody();
        System.out.println(claims);
    }
}
