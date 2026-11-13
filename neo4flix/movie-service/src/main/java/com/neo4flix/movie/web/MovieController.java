package com.neo4flix.movie.web;

import java.time.LocalDate;
import java.util.List;
import com.neo4flix.movie.service.MovieService;
import com.neo4flix.movie.web.MovieDtos.Genre;
import com.neo4flix.movie.web.MovieDtos.Movie;
import com.neo4flix.movie.web.MovieDtos.MovieRequest;
import com.neo4flix.movie.web.MovieDtos.Page;
import com.neo4flix.movie.web.MovieDtos.SearchCriteria;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movies;

    public MovieController(MovieService movies) {
        this.movies = movies;
    }

    /** Example: /api/movies?title=matrix&genre=Action&yearFrom=1990&yearTo=2005&sort=rating */
    @GetMapping
    public Page<Movie> search(@RequestParam(required = false) String title,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) LocalDate releasedAfter,
            @RequestParam(required = false) LocalDate releasedBefore,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "title") String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return movies.search(new SearchCriteria(title, genre, year, yearFrom, yearTo, releasedAfter, releasedBefore,
                minRating, sort, page, size));
    }

    @GetMapping("/genres")
    public List<Genre> genres() {
        return movies.genres();
    }

    @GetMapping("/{id}")
    public Movie get(@PathVariable String id) {
        return movies.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Movie create(@Valid @RequestBody MovieRequest request) {
        return movies.create(request);
    }

    @PutMapping("/{id}")
    public Movie update(@PathVariable String id, @Valid @RequestBody MovieRequest request) {
        return movies.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        movies.delete(id);
    }
}
