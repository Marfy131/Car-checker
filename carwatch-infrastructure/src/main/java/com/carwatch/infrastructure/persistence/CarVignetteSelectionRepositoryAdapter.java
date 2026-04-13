package com.carwatch.infrastructure.persistence;

import com.carwatch.domain.vignette.CarVignetteSelection;
import com.carwatch.domain.vignette.CarVignetteSelectionRepository;
import com.carwatch.domain.vignette.CountryCode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class CarVignetteSelectionRepositoryAdapter implements CarVignetteSelectionRepository {

    private static final DateTimeFormatter SQLITE_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final RowMapper<CarVignetteSelection> MAPPER = CarVignetteSelectionRepositoryAdapter::mapRow;
    private static final String CAR_ID_PARAM = "carId";

    private final NamedParameterJdbcTemplate jdbc;

    public CarVignetteSelectionRepositoryAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public CarVignetteSelection save(CarVignetteSelection selection) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime createdAt = selection.getCreatedAt() != null ? selection.getCreatedAt() : now;
        LocalDateTime updatedAt = selection.getUpdatedAt() != null ? selection.getUpdatedAt() : now;

        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue(CAR_ID_PARAM, selection.getCarId())
                .addValue("country", selection.getCountry().name())
                .addValue("enabled", selection.isEnabled())
                .addValue("createdAt", createdAt)
                .addValue("updatedAt", updatedAt);

        jdbc.update("""
                INSERT INTO car_vignette_selection (car_id, country, enabled, created_at, updated_at)
                VALUES (:carId, :country, :enabled, :createdAt, :updatedAt)
                ON CONFLICT(car_id, country)
                DO UPDATE SET enabled = :enabled, updated_at = :updatedAt
                """, parameters);

        selection.setCreatedAt(createdAt);
        selection.setUpdatedAt(updatedAt);
        return selection;
    }

    @Override
    public List<CarVignetteSelection> findByCarId(Long carId) {
        return jdbc.query(
                "SELECT car_id, country, enabled, created_at, updated_at FROM car_vignette_selection WHERE car_id = :" + CAR_ID_PARAM,
                new MapSqlParameterSource(CAR_ID_PARAM, carId),
                MAPPER
        );
    }

    @Override
    public void deleteByCarId(Long carId) {
        jdbc.update(
                "DELETE FROM car_vignette_selection WHERE car_id = :" + CAR_ID_PARAM,
                new MapSqlParameterSource(CAR_ID_PARAM, carId)
        );
    }

    @Override
    public List<CarVignetteSelection> findAll() {
        return jdbc.query(
                "SELECT car_id, country, enabled, created_at, updated_at FROM car_vignette_selection",
                MAPPER
        );
    }

    private static CarVignetteSelection mapRow(ResultSet rs, int rowNum) throws SQLException {
        CarVignetteSelection selection = new CarVignetteSelection();
        selection.setCarId(rs.getLong("car_id"));
        selection.setCountry(CountryCode.valueOf(rs.getString("country")));
        selection.setEnabled(rs.getBoolean("enabled"));
        selection.setCreatedAt(parseDateTime(rs.getString("created_at")));
        selection.setUpdatedAt(parseDateTime(rs.getString("updated_at")));
        return selection;
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null) {
            return null;
        }
        if (value.contains("T")) {
            return LocalDateTime.parse(value);
        }
        return LocalDateTime.parse(value, SQLITE_DATE_TIME);
    }
}
