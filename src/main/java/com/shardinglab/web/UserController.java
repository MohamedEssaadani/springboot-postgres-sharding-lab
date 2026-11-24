package com.shardinglab.web;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.shardinglab.model.User;
import com.shardinglab.shard.ShardedUser;
import com.shardinglab.shard.UserRepository;

/** Thin HTTP layer: no shard or SQL logic here. Errors are mapped in {@link ApiExceptionHandler}. */
@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository repository;

    public UserController(UserRepository repository) {
        this.repository = repository;
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@RequestBody CreateUserRequest request) {
        if (request.id() == null) {
            throw new IllegalArgumentException("id is required");
        }
        ShardedUser stored = repository.insert(User.newUser(request.id(), request.name(), request.email()));
        return ResponseEntity
                .created(URI.create("/users/" + stored.user().id()))
                .body(UserResponse.from(stored));
    }

    @GetMapping("/{id}")
    public UserResponse get(@PathVariable long id) {
        return repository.findById(id)
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @PutMapping("/{id}")
    public UserResponse update(@PathVariable long id, @RequestBody UpdateUserRequest request) {
        return repository.update(id, request.name(), request.email())
                .map(UserResponse::from)
                .orElseThrow(() -> new UserNotFoundException(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        if (!repository.delete(id)) {
            throw new UserNotFoundException(id);
        }
        return ResponseEntity.noContent().build();
    }
}
