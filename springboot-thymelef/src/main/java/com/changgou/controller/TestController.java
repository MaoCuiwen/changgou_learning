package com.changgou.controller;

import com.changgou.model.User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.*;

@Controller
@RequestMapping("test")
public class TestController {
    /***
     * 访问/test/hello  跳转到demo1页面
     * @param model
     * @return
     */
    @RequestMapping("/hello")
    public String hello(Model model){
        model.addAttribute("hello","hello js");

        //集合数据
        List<User> users = new ArrayList<User>();
        users.add(new User(1,"张三","深圳"));
        users.add(new User(2,"李四","北京"));
        users.add(new User(3,"王五","武汉"));
        model.addAttribute("users",users);

        //Map定义
        Map<String,Object> dataMap = new HashMap<String,Object>();
        dataMap.put("No","123");
        dataMap.put("address","深圳");
        model.addAttribute("dataMap",dataMap);

        //存储一个数组
        String[] names = {"张三","李四","王五"};
        model.addAttribute("names",names);

        //日期
        model.addAttribute("now",new Date());

        //if条件
        model.addAttribute("age",22);

        //字符串拼接
        model.addAttribute("str1", "aaa");
        model.addAttribute("str2", "bbb");

        return "demo1";
    }
}
