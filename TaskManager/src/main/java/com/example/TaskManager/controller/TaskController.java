package com.example.TaskManager.controller;

import com.example.TaskManager.dao.UserRepository;
import com.example.TaskManager.domain.Task;
import com.example.TaskManager.domain.User;
import com.example.TaskManager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.security.Principal;
import java.util.Collection;
import java.util.List;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private static final Logger logger = LoggerFactory.getLogger(TaskController.class);
    private final TaskService taskService;
    private final UserRepository userRepository;

    @Autowired
    public TaskController(TaskService taskService, UserRepository userRepository) {
        this.taskService = taskService;
        this.userRepository = userRepository;
    }

    @GetMapping
    public ResponseEntity<Page<Task>> getAllTasks(Pageable pageable, Principal principal) {
        logger.info("Получение всех задач");
        Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
        Page<Task> tasks;

        if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            tasks = taskService.findAllTasks(pageable);
        } else {
            User currentUser = userRepository.findByUsername(principal.getName());
            tasks = taskService.findTasksByUser(currentUser, pageable);
        }

        return ResponseEntity.ok(tasks);
    }

    @PostMapping
    public ResponseEntity<Task> createTask(@Valid @RequestBody Task task, Principal principal) {
        logger.info("Создание новой задачи: {}", task);

        User currentUser = userRepository.findByUsername(principal.getName());
        if (currentUser == null) {
            logger.error("Пользователь не найден");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        task.setUser(currentUser);
        Task createdTask = taskService.createTask(task);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdTask);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Task> getTaskById(@PathVariable int id) {
        logger.info("Получение задачи с ID: {}", id);
        Task task = taskService.getTaskById(id);
        return task != null ? ResponseEntity.ok(task) : ResponseEntity.notFound().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<Task> updateTask(@PathVariable int id, @Valid @RequestBody Task task) {
        logger.info("Обновление задачи с ID: {}", id);
        task.setId(id);
        Task updatedTask = taskService.updateTask(task);
        return updatedTask != null ? ResponseEntity.ok(updatedTask) : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable int id) {
        logger.info("Удаление задачи с ID: {}", id);
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_ADMIN') or hasRole('ROLE_USER')")
    public ResponseEntity<Page<Task>> searchTasks(@RequestParam(value = "query", required = false) String query, Pageable pageable, Principal principal) {
        logger.info("Поиск задач с ключевым словом: {}", query);
        Page<Task> tasks;

        if (principal != null) {
            Collection<? extends GrantedAuthority> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities();
            if (authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
                tasks = taskService.searchTasks(query, pageable);
            } else {
                User currentUser = userRepository.findByUsername(principal.getName());
                tasks = taskService.searchUserTasks(currentUser, query, pageable);
            }
        } else {
            logger.error("Principal is null");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return ResponseEntity.ok(tasks);
    }

    @PostMapping("/admin/assign")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<?> assignTaskToUser(@RequestParam Long userId, @RequestParam Long taskId) {
        logger.info("Назначение задачи с ID: {} пользователю с ID: {}", taskId, userId);
        Task task = taskService.getTaskById(Math.toIntExact(taskId));
        User user = userRepository.findById(userId).orElse(null);

        if (task != null && user != null) {
            task.setUser(user);
            taskService.updateTask(task);
            return ResponseEntity.ok("Задача успешно назначена");
        } else {
            logger.error("Задача или пользователь не найдены");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Задача или пользователь не найдены");
        }
    }

    @GetMapping("/admin/users")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<List<User>> viewAllUsers() {
        logger.info("Получение списка всех пользователей");
        List<User> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }
}
