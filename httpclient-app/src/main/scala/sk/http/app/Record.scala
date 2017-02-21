package sk.http.app

import sk.httpclient.client.Getable
import java.util.HashMap
import java.util.Map

class Record() extends Getable {
  private var name: String = "defaultName"
  private var age: Integer = 31
  private var city: String = "default city"

  def this(name: String, age: Integer, city: String) {
    this()
    this.name = name
    this.age = age
    this.city = city
  }

  def getName: String = {
    return name
  }

  def setName(name: String) {
    this.name = name
  }

  def getAge: Integer = {
    return age
  }

  def setAge(age: Integer) {
    this.age = age
  }

  def getCity: String = {
    return city
  }

  def setCity(city: String) {
    this.city = city
  }

  override def toString: String = {
    return "Record{" + "name='" + name + '\'' + ", age=" + age + ", city='" + city + '\'' + '}'
  }

  def toMap: java.util.Map[String, String] = {
    val map: java.util.Map[String, String] = new java.util.HashMap[String, String]
    map.put("age", "" + this.age)
    return map
  }
}