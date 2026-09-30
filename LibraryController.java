package com.library;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@Controller
public class LibraryController {
 private final JdbcTemplate db;
 public LibraryController(JdbcTemplate db){this.db=db;}

 @GetMapping("/") public String login(){return "login";}

 @PostMapping("/login")
 public String login(@RequestParam String username,@RequestParam String password,Model model){
  Integer n=db.queryForObject("SELECT COUNT(*) FROM users WHERE username=? AND password=?",
    Integer.class,username,password);
  if(n!=null && n>0) return "redirect:/dashboard";
  model.addAttribute("error","Invalid username or password");
  return "login";
 }

 @GetMapping("/dashboard")
 public String dashboard(Model model){
  model.addAttribute("books",db.queryForList("SELECT * FROM books ORDER BY id DESC"));
  model.addAttribute("members",db.queryForList("SELECT * FROM members ORDER BY id DESC"));
  model.addAttribute("issues",db.queryForList("""
    SELECT i.*,b.title,m.name FROM issued_books i
    JOIN books b ON b.id=i.book_id JOIN members m ON m.id=i.member_id
    ORDER BY i.id DESC"""));
  return "dashboard";
 }

 @PostMapping("/books/add")
 public String addBook(@RequestParam String title,@RequestParam String author,
   @RequestParam String category,@RequestParam int quantity){
  db.update("INSERT INTO books(title,author,category,quantity,available_quantity) VALUES(?,?,?,?,?)",
    title,author,category,quantity,quantity);
  return "redirect:/dashboard";
 }

 @PostMapping("/books/delete/{id}")
 public String deleteBook(@PathVariable int id){
  db.update("DELETE FROM books WHERE id=?",id);
  return "redirect:/dashboard";
 }

 @PostMapping("/members/add")
 public String addMember(@RequestParam String name,@RequestParam String email,@RequestParam String phone){
  db.update("INSERT INTO members(name,email,phone) VALUES(?,?,?)",name,email,phone);
  return "redirect:/dashboard";
 }

 @PostMapping("/members/delete/{id}")
 public String deleteMember(@PathVariable int id){
  db.update("DELETE FROM members WHERE id=?",id);
  return "redirect:/dashboard";
 }

 @PostMapping("/issue")
 @Transactional
 public String issue(@RequestParam int bookId,@RequestParam int memberId,@RequestParam String dueDate){
  Integer available=db.queryForObject("SELECT available_quantity FROM books WHERE id=? FOR UPDATE",Integer.class,bookId);
  if(available==null || available<=0) throw new IllegalArgumentException("No available copies.");
  Integer member=db.queryForObject("SELECT COUNT(*) FROM members WHERE id=?",Integer.class,memberId);
  if(member==null || member==0) throw new IllegalArgumentException("Member not found.");
  db.update("INSERT INTO issued_books(book_id,member_id,issue_date,due_date) VALUES(?,?,?,?)",
      bookId,memberId,LocalDate.now(),LocalDate.parse(dueDate));
  db.update("UPDATE books SET available_quantity=available_quantity-1 WHERE id=?",bookId);
  return "redirect:/dashboard";
 }

 @PostMapping("/return/{id}")
 @Transactional
 public String returnBook(@PathVariable int id){
  Map<String,Object> row=db.queryForMap("SELECT * FROM issued_books WHERE id=? FOR UPDATE",id);
  if(row.get("return_date")!=null) return "redirect:/dashboard";
  int bookId=((Number)row.get("book_id")).intValue();
  LocalDate due=((java.sql.Date)row.get("due_date")).toLocalDate();
  long days=Math.max(0,ChronoUnit.DAYS.between(due,LocalDate.now()));
  db.update("UPDATE issued_books SET return_date=?,fine=? WHERE id=?",
    LocalDate.now(),days*5.0,id);
  db.update("UPDATE books SET available_quantity=available_quantity+1 WHERE id=?",bookId);
  return "redirect:/dashboard";
 }
}