resource "aws_dynamodb_table" "points" {
  name = "points"
  billing_mode = "PROVISIONED"
  read_capacity = 20
  write_capacity = 20
  hash_key = "id"

  attribute {
    name = "id"
    type = "N"
  }
}
