import com.fasterxml.jackson.annotation.{JsonCreator, JsonProperty}

/**
  * Created by Ľubomír Varga on 10.2.2017.
  */
case class Rec @JsonCreator()(
                               @JsonProperty("name")
                               name: String,
                               @JsonProperty("age")
                               age: Integer,
                               @JsonProperty("city")
                               city: String
                             )
