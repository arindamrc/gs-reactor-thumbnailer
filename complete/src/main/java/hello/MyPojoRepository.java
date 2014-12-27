package hello;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MyPojoRepository extends JpaRepository<MyPojo, Integer>{

}
